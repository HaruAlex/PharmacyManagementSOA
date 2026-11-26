package repositories;

import models.SalesStockAllocation;
import models.SalesStockLine;
import play.db.Database;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class SalesStockRepository {
    private final Database database;

    @Inject
    public SalesStockRepository(Database database) {
        this.database = database;
    }

    public List<SalesStockAllocation> deductInvoice(
            Long branchId, Long invoiceId, List<SalesStockLine> lines) {
        return inTransaction(connection -> {
            acquireInvoiceLock(connection, invoiceId);

            List<SalesStockAllocation> existing = findSaleAllocations(connection, invoiceId);
            if (!existing.isEmpty()) {
                validateExistingAllocations(lines, existing);
                return existing;
            }

            List<SalesStockAllocation> allocations = new ArrayList<>();
            for (SalesStockLine line : lines) {
                allocateLine(connection, branchId, invoiceId, line, allocations);
            }
            return allocations;
        });
    }

    public boolean restoreInvoice(Long branchId, Long invoiceId) {
        return inTransaction(connection -> {
            acquireInvoiceLock(connection, invoiceId);
            List<LedgerEntry> saleEntries = findInvoiceEntries(connection, invoiceId, "SALE_OUT");
            if (saleEntries.isEmpty()) {
                return true;
            }

            List<LedgerEntry> returnEntries = findInvoiceEntries(connection, invoiceId, "SALE_RETURN");
            if (!returnEntries.isEmpty()) {
                validateRestoredEntries(saleEntries, returnEntries);
                return true;
            }

            for (LedgerEntry entry : saleEntries) {
                restoreEntry(connection, branchId, invoiceId, entry);
            }
            return false;
        });
    }

    private void allocateLine(
            Connection connection,
            Long branchId,
            Long invoiceId,
            SalesStockLine line,
            List<SalesStockAllocation> allocations) throws SQLException {
        String selectSql = "SELECT l.LotId, l.UnitCostBase, s.QuantityBase " +
                "FROM inventory.DrugLots l WITH (UPDLOCK, ROWLOCK) " +
                "JOIN inventory.StockByLot s WITH (UPDLOCK, ROWLOCK) ON s.LotId = l.LotId " +
                "WHERE l.BranchId = ? AND l.DrugId = ? AND l.IsBlocked = 0 " +
                "AND l.ExpiryDate >= CAST(GETDATE() AS DATE) AND s.QuantityBase > 0 " +
                "ORDER BY l.ExpiryDate, l.ReceivedAt, l.LotId";

        BigDecimal remaining = line.getQuantityBase();
        try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
            statement.setLong(1, branchId);
            statement.setLong(2, line.getDrugId());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next() && remaining.signum() > 0) {
                    long lotId = rows.getLong("LotId");
                    BigDecimal available = rows.getBigDecimal("QuantityBase");
                    BigDecimal quantity = available.min(remaining);
                    BigDecimal cost = rows.getBigDecimal("UnitCostBase");

                    updateStock(connection, lotId, quantity.negate());
                    insertLedger(connection, branchId, line.getDrugId(), lotId,
                            "SALE_OUT", invoiceId, quantity.negate(), cost,
                            "INVOICE_DETAIL:" + line.getInvoiceDetailId());
                    allocations.add(new SalesStockAllocation(
                            line.getInvoiceDetailId(), line.getDrugId(), lotId, quantity, cost));
                    remaining = remaining.subtract(quantity);
                }
            }
        }

        if (remaining.signum() > 0) {
            throw new IllegalStateException(
                    "Insufficient eligible stock for drug " + line.getDrugId()
                            + "; unavailable quantity: " + remaining);
        }
    }

    private List<SalesStockAllocation> findSaleAllocations(Connection connection, Long invoiceId)
            throws SQLException {
        String sql = "SELECT DrugId, LotId, QuantityChange, UnitCostBase, Note " +
                "FROM inventory.StockLedger WITH (UPDLOCK, HOLDLOCK) " +
                "WHERE TransactionType = 'SALE_OUT' AND ReferenceType = 'INVOICE' AND ReferenceId = ?";
        List<SalesStockAllocation> allocations = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, invoiceId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Long invoiceDetailId = invoiceDetailIdFromNote(rows.getString("Note"));
                    BigDecimal cost = rows.getBigDecimal("UnitCostBase");
                    allocations.add(new SalesStockAllocation(
                            invoiceDetailId,
                            rows.getLong("DrugId"),
                            rows.getLong("LotId"),
                            rows.getBigDecimal("QuantityChange").abs(),
                            cost == null ? BigDecimal.ZERO : cost));
                }
            }
        }
        return allocations;
    }

    private List<LedgerEntry> findInvoiceEntries(Connection connection, Long invoiceId, String type)
            throws SQLException {
        String sql = "SELECT BranchId, DrugId, LotId, QuantityChange, UnitCostBase, Note " +
                "FROM inventory.StockLedger WITH (UPDLOCK, HOLDLOCK) " +
                "WHERE TransactionType = ? AND ReferenceType = 'INVOICE' AND ReferenceId = ?";
        List<LedgerEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type);
            statement.setLong(2, invoiceId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    entries.add(new LedgerEntry(
                            rows.getInt("BranchId"),
                            rows.getInt("DrugId"),
                            rows.getLong("LotId"),
                            rows.getBigDecimal("QuantityChange"),
                            rows.getBigDecimal("UnitCostBase"),
                            rows.getString("Note")));
                }
            }
        }
        return entries;
    }

    private void restoreEntry(
            Connection connection, Long branchId, Long invoiceId, LedgerEntry entry)
            throws SQLException {
        if (entry.branchId != branchId.intValue()) {
            throw new IllegalStateException("Invoice stock belongs to a different branch");
        }
        BigDecimal restoredQuantity = entry.quantityChange.abs();
        updateStock(connection, entry.lotId, restoredQuantity);
        insertLedger(connection, branchId, (long) entry.drugId, entry.lotId,
                "SALE_RETURN", invoiceId, restoredQuantity, entry.unitCost,
                entry.note);
    }

    private void updateStock(Connection connection, Long lotId, BigDecimal delta)
            throws SQLException {
        String sql = "UPDATE inventory.StockByLot " +
                "SET QuantityBase = QuantityBase + ?, UpdatedAt = SYSDATETIME() WHERE LotId = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, delta);
            statement.setLong(2, lotId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Stock row does not exist for lot " + lotId);
            }
        }
    }

    private void insertLedger(
            Connection connection,
            Long branchId,
            Long drugId,
            Long lotId,
            String type,
            Long invoiceId,
            BigDecimal quantity,
            BigDecimal unitCost,
            String note) throws SQLException {
        String sql = "INSERT INTO inventory.StockLedger " +
                "(BranchId, DrugId, LotId, TransactionType, ReferenceType, ReferenceId, " +
                "QuantityChange, UnitCostBase, CreatedBy, Note) " +
                "VALUES (?, ?, ?, ?, 'INVOICE', ?, ?, ?, NULL, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, branchId);
            statement.setLong(2, drugId);
            statement.setLong(3, lotId);
            statement.setString(4, type);
            statement.setLong(5, invoiceId);
            statement.setBigDecimal(6, quantity);
            if (unitCost == null) {
                statement.setNull(7, Types.DECIMAL);
            } else {
                statement.setBigDecimal(7, unitCost);
            }
            statement.setString(8, note);
            statement.executeUpdate();
        }
    }

    private void acquireInvoiceLock(Connection connection, Long invoiceId) throws SQLException {
        String sql = "DECLARE @result int; " +
                "EXEC @result = sys.sp_getapplock @Resource = ?, @LockMode = 'Exclusive', " +
                "@LockOwner = 'Transaction', @LockTimeout = 10000; SELECT @result";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "inventory-invoice-" + invoiceId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) < 0) {
                    throw new IllegalStateException("Could not lock inventory for invoice " + invoiceId);
                }
            }
        }
    }

    private void validateExistingAllocations(
            List<SalesStockLine> requested, List<SalesStockAllocation> existing) {
        Map<Long, BigDecimal> expected = new HashMap<>();
        Map<Long, Long> expectedDrugs = new HashMap<>();
        for (SalesStockLine line : requested) {
            expected.put(line.getInvoiceDetailId(), line.getQuantityBase());
            expectedDrugs.put(line.getInvoiceDetailId(), line.getDrugId());
        }
        Map<Long, BigDecimal> actual = new HashMap<>();
        Map<Long, Long> actualDrugs = new HashMap<>();
        for (SalesStockAllocation allocation : existing) {
            if (allocation.getInvoiceDetailId() == null) {
                throw new IllegalStateException("Existing inventory ledger entry has no invoice detail");
            }
            actual.merge(allocation.getInvoiceDetailId(), allocation.getQuantityBase(), BigDecimal::add);
            Long previousDrugId = actualDrugs.put(
                    allocation.getInvoiceDetailId(), allocation.getDrugId());
            if (previousDrugId != null && !previousDrugId.equals(allocation.getDrugId())) {
                throw new IllegalStateException("Existing invoice detail references multiple drugs");
            }
        }
        if (!quantitiesMatch(expected, actual) || !expectedDrugs.equals(actualDrugs)) {
            throw new IllegalStateException("Invoice has already been deducted with different line quantities");
        }
    }

    private void validateRestoredEntries(List<LedgerEntry> saleEntries, List<LedgerEntry> returnEntries) {
        Map<Long, BigDecimal> soldByLot = totalByLot(saleEntries);
        Map<Long, BigDecimal> returnedByLot = totalByLot(returnEntries);
        if (!quantitiesMatch(soldByLot, returnedByLot)) {
            throw new IllegalStateException("Invoice has a partial stock restoration");
        }
    }

    private boolean quantitiesMatch(
            Map<Long, BigDecimal> expected, Map<Long, BigDecimal> actual) {
        if (!expected.keySet().equals(actual.keySet())) {
            return false;
        }
        for (Long key : expected.keySet()) {
            if (expected.get(key).compareTo(actual.get(key)) != 0) {
                return false;
            }
        }
        return true;
    }

    private Map<Long, BigDecimal> totalByLot(List<LedgerEntry> entries) {
        Map<Long, BigDecimal> totals = new HashMap<>();
        for (LedgerEntry entry : entries) {
            totals.merge(entry.lotId, entry.quantityChange.abs(), BigDecimal::add);
        }
        return totals;
    }

    private Long invoiceDetailIdFromNote(String note) {
        String prefix = "INVOICE_DETAIL:";
        if (note == null || !note.startsWith(prefix)) {
            return null;
        }
        try {
            return Long.parseLong(note.substring(prefix.length()));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid invoice detail reference in stock ledger", e);
        }
    }

    private <T> T inTransaction(SqlWork<T> work) {
        try (Connection connection = database.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (Exception e) {
                connection.rollback();
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException("Inventory transaction failed", e);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Inventory database operation failed", e);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws Exception;
    }

    private static final class LedgerEntry {
        private final int branchId;
        private final int drugId;
        private final long lotId;
        private final BigDecimal quantityChange;
        private final BigDecimal unitCost;
        private final String note;

        private LedgerEntry(
                int branchId, int drugId, long lotId, BigDecimal quantityChange,
                BigDecimal unitCost, String note) {
            this.branchId = branchId;
            this.drugId = drugId;
            this.lotId = lotId;
            this.quantityChange = quantityChange;
            this.unitCost = unitCost;
            this.note = note;
        }
    }
}
