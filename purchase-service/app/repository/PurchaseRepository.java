package repository;

import play.db.Database;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class PurchaseRepository {
    private final Database database;

    @Inject
    public PurchaseRepository(Database database) {
        this.database = database;
    }

    public boolean supplierExists(int supplierId) throws SQLException {
        return exists("SELECT 1 FROM catalog.Suppliers WHERE SupplierId=? AND IsActive=1", supplierId);
    }

    public boolean branchExists(int branchId) throws SQLException {
        return exists("SELECT 1 FROM catalog.Branches WHERE BranchId=? AND IsActive=1", branchId);
    }

    public boolean userExists(int userId) throws SQLException {
        return exists("SELECT 1 FROM auth.Users WHERE UserId=? AND IsActive=1", userId);
    }

    public boolean drugExists(int drugId) throws SQLException {
        return exists("SELECT 1 FROM catalog.Drugs WHERE DrugId=? AND IsActive=1", drugId);
    }

    private boolean exists(String sql, int id) throws SQLException {
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public long createPurchaseOrder(String code, int supplierId, int branchId, int createdBy,
                                    Timestamp orderDate, String note, List<OrderDetail> details) throws SQLException {
        Connection c = database.getConnection();
        try {
            c.setAutoCommit(false);
            long orderId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO purchase.PurchaseOrders(OrderCode,SupplierId,BranchId,CreatedBy,OrderDate,Status,Note) " +
                    "VALUES(?,?,?,?,?, 'DRAFT',?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, code);
                ps.setInt(2, supplierId);
                ps.setInt(3, branchId);
                ps.setInt(4, createdBy);
                ps.setTimestamp(5, orderDate);
                ps.setString(6, note);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) throw new SQLException("Cannot create purchase order");
                    orderId = rs.getLong(1);
                }
            }
            for (OrderDetail d : details) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO purchase.PurchaseOrderDetails(PurchaseOrderId,DrugId,QuantityBase,ExpectedUnitCost) VALUES(?,?,?,?)")) {
                    ps.setLong(1, orderId);
                    ps.setInt(2, d.drugId);
                    ps.setBigDecimal(3, d.quantity);
                    ps.setBigDecimal(4, d.unitCost);
                    ps.executeUpdate();
                }
            }
            c.commit();
            return orderId;
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.close();
        }
    }

    public List<PurchaseRow> listPurchaseOrders(String keyword, Integer supplierId, String status) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT o.PurchaseOrderId,o.OrderCode,o.SupplierId,o.BranchId,o.CreatedBy,o.OrderDate,o.Status,o.Note " +
                "FROM purchase.PurchaseOrders o WHERE 1=1");
        if (keyword != null && !keyword.isBlank()) sql.append(" AND o.OrderCode LIKE ?");
        if (supplierId != null) sql.append(" AND o.SupplierId=?");
        if (status != null && !status.isBlank()) sql.append(" AND o.Status=?");
        sql.append(" ORDER BY o.PurchaseOrderId DESC");
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int i = 1;
            if (keyword != null && !keyword.isBlank()) ps.setString(i++, "%" + keyword + "%");
            if (supplierId != null) ps.setInt(i++, supplierId);
            if (status != null && !status.isBlank()) ps.setString(i++, status);
            List<PurchaseRow> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(PurchaseRow.from(rs));
            }
            return rows;
        }
    }

    public PurchaseRow getPurchaseOrder(long id) throws SQLException {
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT PurchaseOrderId,OrderCode,SupplierId,BranchId,CreatedBy,OrderDate,Status,Note " +
                     "FROM purchase.PurchaseOrders WHERE PurchaseOrderId=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? PurchaseRow.from(rs) : null;
            }
        }
    }

    public List<DetailRow> getPurchaseOrderDetails(long id) throws SQLException {
        return queryDetails("SELECT PurchaseOrderDetailId,PurchaseOrderId,DrugId,QuantityBase,ExpectedUnitCost " +
                "FROM purchase.PurchaseOrderDetails WHERE PurchaseOrderId=? ORDER BY PurchaseOrderDetailId", id);
    }

    public long addPurchaseOrderDetail(long orderId, OrderDetail detail) throws SQLException {
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO purchase.PurchaseOrderDetails(PurchaseOrderId,DrugId,QuantityBase,ExpectedUnitCost) " +
                     "VALUES(?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, orderId);
            ps.setInt(2, detail.drugId);
            ps.setBigDecimal(3, detail.quantity);
            ps.setBigDecimal(4, detail.unitCost);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) throw new SQLException("Cannot create purchase order detail");
                return rs.getLong(1);
            }
        }
    }

    public boolean updatePurchaseOrderDetail(long orderId, long detailId, OrderDetail detail) throws SQLException {
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE purchase.PurchaseOrderDetails SET DrugId=?,QuantityBase=?,ExpectedUnitCost=? " +
                     "WHERE PurchaseOrderId=? AND PurchaseOrderDetailId=?")) {
            ps.setInt(1, detail.drugId);
            ps.setBigDecimal(2, detail.quantity);
            ps.setBigDecimal(3, detail.unitCost);
            ps.setLong(4, orderId);
            ps.setLong(5, detailId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deletePurchaseOrderDetail(long orderId, long detailId) throws SQLException {
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM purchase.PurchaseOrderDetails WHERE PurchaseOrderId=? AND PurchaseOrderDetailId=?")) {
            ps.setLong(1, orderId);
            ps.setLong(2, detailId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updatePurchaseOrder(long id, Integer supplierId, Integer branchId, Timestamp orderDate,
                                       boolean noteProvided, String note, String status, String expectedStatus) throws SQLException {
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE purchase.PurchaseOrders SET SupplierId=COALESCE(?,SupplierId),BranchId=COALESCE(?,BranchId)," +
                     "OrderDate=COALESCE(?,OrderDate),Note=CASE WHEN ?=1 THEN ? ELSE Note END," +
                     "Status=COALESCE(?,Status) WHERE PurchaseOrderId=? AND Status=?")) {
            if (supplierId == null) ps.setNull(1, Types.INTEGER); else ps.setInt(1, supplierId);
            if (branchId == null) ps.setNull(2, Types.INTEGER); else ps.setInt(2, branchId);
            if (orderDate == null) ps.setNull(3, Types.TIMESTAMP); else ps.setTimestamp(3, orderDate);
            ps.setBoolean(4, noteProvided);
            ps.setString(5, note);
            if (status == null) ps.setNull(6, Types.VARCHAR); else ps.setString(6, status);
            ps.setLong(7, id);
            ps.setString(8, expectedStatus);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean cancelPurchaseOrder(long id) throws SQLException {
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE purchase.PurchaseOrders SET Status='CANCELLED' WHERE PurchaseOrderId=? AND Status='DRAFT'")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public long createReceipt(String code, Long purchaseOrderId, int supplierId, int branchId, int createdBy,
                              Timestamp receiptDate, String note, List<ReceiptDetail> details) throws SQLException {
        Connection c = database.getConnection();
        try {
            c.setAutoCommit(false);
            BigDecimal total = details.stream().map(d -> d.quantity.multiply(d.unitCost))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long receiptId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO purchase.PurchaseReceipts(ReceiptCode,PurchaseOrderId,SupplierId,BranchId,CreatedBy,ReceiptDate,Status,TotalAmount,Note) " +
                    "VALUES(?,?,?,?,?,?, 'DRAFT',?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, code);
                if (purchaseOrderId == null) ps.setNull(2, Types.BIGINT); else ps.setLong(2, purchaseOrderId);
                ps.setInt(3, supplierId);
                ps.setInt(4, branchId);
                ps.setInt(5, createdBy);
                ps.setTimestamp(6, receiptDate);
                ps.setBigDecimal(7, total);
                ps.setString(8, note);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) throw new SQLException("Cannot create purchase receipt");
                    receiptId = rs.getLong(1);
                }
            }
            insertReceiptDetails(c, receiptId, details);
            c.commit();
            return receiptId;
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.close();
        }
    }

    private void insertReceiptDetails(Connection c, long receiptId, List<ReceiptDetail> details) throws SQLException {
        for (ReceiptDetail d : details) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO purchase.PurchaseReceiptDetails(PurchaseReceiptId,DrugId,LotNumber,ExpiryDate,ManufactureDate,QuantityBase,UnitCostBase) " +
                    "VALUES(?,?,?,?,?,?,?)")) {
                ps.setLong(1, receiptId);
                ps.setInt(2, d.drugId);
                ps.setString(3, d.lotNumber);
                ps.setDate(4, Date.valueOf(d.expiryDate));
                if (d.manufactureDate == null) ps.setNull(5, Types.DATE); else ps.setDate(5, Date.valueOf(d.manufactureDate));
                ps.setBigDecimal(6, d.quantity);
                ps.setBigDecimal(7, d.unitCost);
                ps.executeUpdate();
            }
        }
    }

    public ReceiptRow getReceipt(long id) throws SQLException {
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT PurchaseReceiptId,ReceiptCode,PurchaseOrderId,SupplierId,BranchId,CreatedBy,ApprovedBy,ReceiptDate,Status,TotalAmount,Note " +
                     "FROM purchase.PurchaseReceipts WHERE PurchaseReceiptId=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? ReceiptRow.from(rs) : null;
            }
        }
    }

    public List<ReceiptRow> searchReceipts(Timestamp fromDate, Timestamp toDate, Integer supplierId, String status) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT PurchaseReceiptId,ReceiptCode,PurchaseOrderId,SupplierId,BranchId,CreatedBy,ApprovedBy,ReceiptDate,Status,TotalAmount,Note " +
                "FROM purchase.PurchaseReceipts WHERE 1=1");
        if (fromDate != null) sql.append(" AND ReceiptDate>=?");
        if (toDate != null) sql.append(" AND ReceiptDate<=?");
        if (supplierId != null) sql.append(" AND SupplierId=?");
        if (status != null && !status.isBlank()) sql.append(" AND Status=?");
        sql.append(" ORDER BY ReceiptDate DESC, PurchaseReceiptId DESC");
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int i = 1;
            if (fromDate != null) ps.setTimestamp(i++, fromDate);
            if (toDate != null) ps.setTimestamp(i++, toDate);
            if (supplierId != null) ps.setInt(i++, supplierId);
            if (status != null && !status.isBlank()) ps.setString(i++, status);
            List<ReceiptRow> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(ReceiptRow.from(rs));
            }
            return rows;
        }
    }

    public List<ReceiptDetailRow> getReceiptDetails(long receiptId) throws SQLException {
        return queryReceiptDetails(receiptId);
    }

    private List<ReceiptDetailRow> queryReceiptDetails(long receiptId) throws SQLException {
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT PurchaseReceiptDetailId,PurchaseReceiptId,DrugId,LotNumber,ExpiryDate,ManufactureDate,QuantityBase,UnitCostBase " +
                     "FROM purchase.PurchaseReceiptDetails WHERE PurchaseReceiptId=? ORDER BY PurchaseReceiptDetailId")) {
            ps.setLong(1, receiptId);
            List<ReceiptDetailRow> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(ReceiptDetailRow.from(rs));
            }
            return rows;
        }
    }

    public boolean receiptInEditableStatus(long receiptId) throws SQLException {
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT Status FROM purchase.PurchaseReceipts WHERE PurchaseReceiptId=?")) {
            ps.setLong(1, receiptId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                String status = rs.getString(1);
                return "DRAFT".equals(status) || "SUBMITTED".equals(status);
            }
        }
    }

    public long addReceiptDetail(long receiptId, ReceiptDetail d) throws SQLException {
        Connection c = database.getConnection();
        try {
            c.setAutoCommit(false);
            long detailId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO purchase.PurchaseReceiptDetails(PurchaseReceiptId,DrugId,LotNumber,ExpiryDate,ManufactureDate,QuantityBase,UnitCostBase) " +
                    "VALUES(?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, receiptId);
                ps.setInt(2, d.drugId);
                ps.setString(3, d.lotNumber);
                ps.setDate(4, Date.valueOf(d.expiryDate));
                if (d.manufactureDate == null) ps.setNull(5, Types.DATE); else ps.setDate(5, Date.valueOf(d.manufactureDate));
                ps.setBigDecimal(6, d.quantity);
                ps.setBigDecimal(7, d.unitCost);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) throw new SQLException("Cannot create receipt detail");
                    detailId = rs.getLong(1);
                }
            }
            recalculateReceiptTotal(c, receiptId);
            c.commit();
            return detailId;
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.close();
        }
    }

    public boolean updateReceipt(long id, Long purchaseOrderId, Integer supplierId, Integer branchId,
                                 Timestamp receiptDate, String note) throws SQLException {
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE purchase.PurchaseReceipts SET PurchaseOrderId=COALESCE(?,PurchaseOrderId)," +
                     "SupplierId=COALESCE(?,SupplierId),BranchId=COALESCE(?,BranchId)," +
                     "ReceiptDate=COALESCE(?,ReceiptDate),Note=? WHERE PurchaseReceiptId=?")) {
            if (purchaseOrderId == null) ps.setNull(1, Types.BIGINT); else ps.setLong(1, purchaseOrderId);
            if (supplierId == null) ps.setNull(2, Types.INTEGER); else ps.setInt(2, supplierId);
            if (branchId == null) ps.setNull(3, Types.INTEGER); else ps.setInt(3, branchId);
            if (receiptDate == null) ps.setNull(4, Types.TIMESTAMP); else ps.setTimestamp(4, receiptDate);
            ps.setString(5, note);
            ps.setLong(6, id);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateReceiptDetail(long detailId, ReceiptDetail d) throws SQLException {
        Connection c = database.getConnection();
        try {
            c.setAutoCommit(false);
            long receiptId;
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE purchase.PurchaseReceiptDetails SET DrugId=?,LotNumber=?,ExpiryDate=?,ManufactureDate=?,QuantityBase=?,UnitCostBase=? " +
                    "OUTPUT INSERTED.PurchaseReceiptId WHERE PurchaseReceiptDetailId=?")) {
                ps.setInt(1, d.drugId);
                ps.setString(2, d.lotNumber);
                ps.setDate(3, Date.valueOf(d.expiryDate));
                if (d.manufactureDate == null) ps.setNull(4, Types.DATE); else ps.setDate(4, Date.valueOf(d.manufactureDate));
                ps.setBigDecimal(5, d.quantity);
                ps.setBigDecimal(6, d.unitCost);
                ps.setLong(7, detailId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        c.rollback();
                        return false;
                    }
                    receiptId = rs.getLong(1);
                }
            }
            recalculateReceiptTotal(c, receiptId);
            c.commit();
            return true;
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.close();
        }
    }

    public boolean deleteReceiptDetail(long detailId) throws SQLException {
        Connection c = database.getConnection();
        try {
            c.setAutoCommit(false);
            long receiptId;
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM purchase.PurchaseReceiptDetails OUTPUT DELETED.PurchaseReceiptId WHERE PurchaseReceiptDetailId=?")) {
                ps.setLong(1, detailId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        c.rollback();
                        return false;
                    }
                    receiptId = rs.getLong(1);
                }
            }
            recalculateReceiptTotal(c, receiptId);
            c.commit();
            return true;
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.close();
        }
    }

    public boolean getReceiptDetailOwnership(long receiptId, long detailId) throws SQLException {
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM purchase.PurchaseReceiptDetails WHERE PurchaseReceiptId=? AND PurchaseReceiptDetailId=?")) {
            ps.setLong(1, receiptId);
            ps.setLong(2, detailId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public String getReceiptStatus(long receiptId) throws SQLException {
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT Status FROM purchase.PurchaseReceipts WHERE PurchaseReceiptId=?")) {
            ps.setLong(1, receiptId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    public boolean transitionReceipt(long receiptId, String newStatus, Integer approvedBy) throws SQLException {
        Connection c = database.getConnection();
        try {
            c.setAutoCommit(false);
            String current;
            try (PreparedStatement ps = c.prepareStatement("SELECT Status FROM purchase.PurchaseReceipts WITH (UPDLOCK,ROWLOCK) WHERE PurchaseReceiptId=?")) {
                ps.setLong(1, receiptId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return false;
                    current = rs.getString(1);
                }
            }
            boolean valid = ("DRAFT".equals(current) && "SUBMITTED".equals(newStatus))
                    || ("DRAFT".equals(current) && "CANCELLED".equals(newStatus))
                    || ("SUBMITTED".equals(current) && "APPROVED".equals(newStatus))
                    || ("SUBMITTED".equals(current) && "CANCELLED".equals(newStatus));
            if (!valid) throw new IllegalArgumentException("Invalid receipt status transition: " + current + " -> " + newStatus);
            if ("APPROVED".equals(newStatus)) {
                approveInventoryAndReceipt(c, receiptId, approvedBy);
            } else {
                try (PreparedStatement ps = c.prepareStatement("UPDATE purchase.PurchaseReceipts SET Status=? WHERE PurchaseReceiptId=?")) {
                    ps.setString(1, newStatus);
                    ps.setLong(2, receiptId);
                    ps.executeUpdate();
                }
            }
            c.commit();
            return true;
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.close();
        }
    }

    private void approveInventoryAndReceipt(Connection c, long receiptId, Integer approvedBy) throws SQLException {
        int branchId;
        int createdBy;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT BranchId,CreatedBy FROM purchase.PurchaseReceipts WHERE PurchaseReceiptId=?")) {
            ps.setLong(1, receiptId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                branchId = rs.getInt("BranchId");
                createdBy = rs.getInt("CreatedBy");
            }
        }
        int actor = approvedBy != null ? approvedBy : createdBy;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT PurchaseReceiptDetailId,DrugId,LotNumber,ExpiryDate,ManufactureDate,QuantityBase,UnitCostBase " +
                "FROM purchase.PurchaseReceiptDetails WHERE PurchaseReceiptId=?")) {
            ps.setLong(1, receiptId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long detailId = rs.getLong("PurchaseReceiptDetailId");
                    int drugId = rs.getInt("DrugId");
                    String lotNumber = rs.getString("LotNumber");
                    Date expiry = rs.getDate("ExpiryDate");
                    Date manufacture = rs.getDate("ManufactureDate");
                    BigDecimal quantity = rs.getBigDecimal("QuantityBase");
                    BigDecimal cost = rs.getBigDecimal("UnitCostBase");
                    long lotId = ensureLot(c, branchId, drugId, detailId, lotNumber, manufacture, expiry, cost);
                    try (PreparedStatement stock = c.prepareStatement(
                            "UPDATE inventory.StockByLot SET QuantityBase=QuantityBase+?,UpdatedAt=SYSDATETIME() WHERE LotId=?")) {
                        stock.setBigDecimal(1, quantity);
                        stock.setLong(2, lotId);
                        if (stock.executeUpdate() == 0) {
                            try (PreparedStatement insert = c.prepareStatement(
                                    "INSERT INTO inventory.StockByLot(LotId,QuantityBase) VALUES(?,?)")) {
                                insert.setLong(1, lotId);
                                insert.setBigDecimal(2, quantity);
                                insert.executeUpdate();
                            }
                        }
                    }
                    try (PreparedStatement ledger = c.prepareStatement(
                            "INSERT INTO inventory.StockLedger(BranchId,DrugId,LotId,TransactionType,ReferenceType,ReferenceId,QuantityChange,UnitCostBase,CreatedBy,Note) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                        ledger.setInt(1, branchId);
                        ledger.setInt(2, drugId);
                        ledger.setLong(3, lotId);
                        ledger.setString(4, "PURCHASE_IN");
                        ledger.setString(5, "PURCHASE_RECEIPT");
                        ledger.setLong(6, receiptId);
                        ledger.setBigDecimal(7, quantity);
                        ledger.setBigDecimal(8, cost);
                        ledger.setInt(9, actor);
                        ledger.setString(10, "Approved purchase receipt");
                        ledger.executeUpdate();
                    }
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE purchase.PurchaseReceipts SET Status='APPROVED',ApprovedBy=? WHERE PurchaseReceiptId=?")) {
            ps.setInt(1, actor);
            ps.setLong(2, receiptId);
            ps.executeUpdate();
        }
    }

    private long ensureLot(Connection c, int branchId, int drugId, long detailId, String lotNumber,
                           Date manufacture, Date expiry, BigDecimal cost) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT LotId,ManufactureDate,ExpiryDate FROM inventory.DrugLots " +
                "WHERE BranchId=? AND DrugId=? AND LotNumber=?")) {
            ps.setInt(1, branchId);
            ps.setInt(2, drugId);
            ps.setString(3, lotNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Date existingManufacture = rs.getDate("ManufactureDate");
                    Date existingExpiry = rs.getDate("ExpiryDate");
                    if (!java.util.Objects.equals(existingManufacture, manufacture)
                            || !java.util.Objects.equals(existingExpiry, expiry)) {
                        throw new SQLException("Lot number already exists with different dates", "23000");
                    }
                    return rs.getLong("LotId");
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO inventory.DrugLots(BranchId,DrugId,PurchaseReceiptDetailId,LotNumber,ManufactureDate,ExpiryDate,UnitCostBase) " +
                "VALUES(?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, branchId);
            ps.setInt(2, drugId);
            ps.setLong(3, detailId);
            ps.setString(4, lotNumber);
            if (manufacture == null) ps.setNull(5, Types.DATE); else ps.setDate(5, manufacture);
            ps.setDate(6, expiry);
            ps.setBigDecimal(7, cost);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private void recalculateReceiptTotal(Connection c, long receiptId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE purchase.PurchaseReceipts SET TotalAmount=(" +
                "SELECT SUM(QuantityBase*UnitCostBase) FROM purchase.PurchaseReceiptDetails WHERE PurchaseReceiptId=?" +
                ") WHERE PurchaseReceiptId=?")) {
            ps.setLong(1, receiptId);
            ps.setLong(2, receiptId);
            ps.executeUpdate();
        }
    }

    public long createSupplierReturn(String code, int supplierId, int branchId, int createdBy, Timestamp returnDate,
                                     String reason, List<ReturnDetail> details) throws SQLException {
        Connection c = database.getConnection();
        try {
            c.setAutoCommit(false);
            long returnId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO purchase.SupplierReturns(ReturnCode,SupplierId,BranchId,CreatedBy,ReturnDate,Status,Reason) " +
                    "VALUES(?,?,?,?,?, 'DRAFT',?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, code);
                ps.setInt(2, supplierId);
                ps.setInt(3, branchId);
                ps.setInt(4, createdBy);
                ps.setTimestamp(5, returnDate);
                ps.setString(6, reason);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) throw new SQLException("Cannot create supplier return");
                    returnId = rs.getLong(1);
                }
            }
            for (ReturnDetail d : details) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO purchase.SupplierReturnDetails(SupplierReturnId,LotId,QuantityBase,UnitCostBase) VALUES(?,?,?,?)")) {
                    ps.setLong(1, returnId);
                    ps.setLong(2, d.lotId);
                    ps.setBigDecimal(3, d.quantity);
                    ps.setBigDecimal(4, d.unitCost);
                    ps.executeUpdate();
                }
            }
            c.commit();
            return returnId;
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.close();
        }
    }

    public ReturnRow getSupplierReturn(long id) throws SQLException {
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT SupplierReturnId,ReturnCode,SupplierId,BranchId,CreatedBy,ReturnDate,Status,Reason " +
                     "FROM purchase.SupplierReturns WHERE SupplierReturnId=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? ReturnRow.from(rs) : null;
            }
        }
    }

    public List<ReturnRow> listSupplierReturns(Integer supplierId, String status) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT SupplierReturnId,ReturnCode,SupplierId,BranchId,CreatedBy,ReturnDate,Status,Reason " +
                "FROM purchase.SupplierReturns WHERE 1=1");
        if (supplierId != null) sql.append(" AND SupplierId=?");
        if (status != null && !status.isBlank()) sql.append(" AND Status=?");
        sql.append(" ORDER BY SupplierReturnId DESC");
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int i = 1;
            if (supplierId != null) ps.setInt(i++, supplierId);
            if (status != null && !status.isBlank()) ps.setString(i++, status);
            List<ReturnRow> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(ReturnRow.from(rs));
            }
            return rows;
        }
    }

    public List<ReturnDetailRow> getSupplierReturnDetails(long returnId) throws SQLException {
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT SupplierReturnDetailId,SupplierReturnId,LotId,QuantityBase,UnitCostBase " +
                     "FROM purchase.SupplierReturnDetails WHERE SupplierReturnId=? ORDER BY SupplierReturnDetailId")) {
            ps.setLong(1, returnId);
            List<ReturnDetailRow> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(ReturnDetailRow.from(rs));
            }
            return rows;
        }
    }

    public boolean confirmSupplierReturn(long returnId, int createdBy) throws SQLException {
        Connection c = database.getConnection();
        try {
            c.setAutoCommit(false);
            String status;
            int branchId;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT Status,BranchId FROM purchase.SupplierReturns WITH (UPDLOCK,ROWLOCK) WHERE SupplierReturnId=?")) {
                ps.setLong(1, returnId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return false;
                    status = rs.getString("Status");
                    branchId = rs.getInt("BranchId");
                }
            }
            if (!"DRAFT".equals(status)) throw new IllegalArgumentException("Only DRAFT supplier return can be confirmed");
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT d.LotId,d.QuantityBase,d.UnitCostBase,l.DrugId " +
                    "FROM purchase.SupplierReturnDetails d JOIN inventory.DrugLots l ON l.LotId=d.LotId " +
                    "WHERE d.SupplierReturnId=?")) {
                ps.setLong(1, returnId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long lotId = rs.getLong("LotId");
                        int drugId = rs.getInt("DrugId");
                        BigDecimal quantity = rs.getBigDecimal("QuantityBase");
                        BigDecimal cost = rs.getBigDecimal("UnitCostBase");
                        try (PreparedStatement stock = c.prepareStatement(
                                "UPDATE inventory.StockByLot SET QuantityBase=QuantityBase-?,UpdatedAt=SYSDATETIME() " +
                                "WHERE LotId=? AND QuantityBase>=?")) {
                            stock.setBigDecimal(1, quantity);
                            stock.setLong(2, lotId);
                            stock.setBigDecimal(3, quantity);
                            if (stock.executeUpdate() != 1) throw new SQLException("Return quantity exceeds available stock");
                        }
                        try (PreparedStatement ledger = c.prepareStatement(
                                "INSERT INTO inventory.StockLedger(BranchId,DrugId,LotId,TransactionType,ReferenceType,ReferenceId,QuantityChange,UnitCostBase,CreatedBy,Note) " +
                                "VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                            ledger.setInt(1, branchId);
                            ledger.setInt(2, drugId);
                            ledger.setLong(3, lotId);
                            ledger.setString(4, "SUPPLIER_RETURN");
                            ledger.setString(5, "SUPPLIER_RETURN");
                            ledger.setLong(6, returnId);
                            ledger.setBigDecimal(7, quantity.negate());
                            ledger.setBigDecimal(8, cost);
                            ledger.setInt(9, createdBy);
                            ledger.setString(10, "Supplier return confirmed");
                            ledger.executeUpdate();
                        }
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE purchase.SupplierReturns SET Status='APPROVED' WHERE SupplierReturnId=?")) {
                ps.setLong(1, returnId);
                ps.executeUpdate();
            }
            c.commit();
            return true;
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.close();
        }
    }

    public boolean cancelSupplierReturn(long returnId) throws SQLException {
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE purchase.SupplierReturns SET Status='CANCELLED' WHERE SupplierReturnId=? AND Status='DRAFT'")) {
            ps.setLong(1, returnId);
            return ps.executeUpdate() > 0;
        }
    }

    public long createSupplierPayment(String code, int supplierId, Integer branchId, int paidBy, Timestamp paymentDate,
                                      BigDecimal amount, String method, String note) throws SQLException {
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO purchase.SupplierPayments(PaymentCode,SupplierId,BranchId,PaidBy,PaymentDate,Amount,PaymentMethod,Note) " +
                     "VALUES(?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, code);
            ps.setInt(2, supplierId);
            if (branchId == null) ps.setNull(3, Types.INTEGER); else ps.setInt(3, branchId);
            ps.setInt(4, paidBy);
            ps.setTimestamp(5, paymentDate);
            ps.setBigDecimal(6, amount);
            ps.setString(7, method);
            ps.setString(8, note);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public PaymentRow getSupplierPayment(long id) throws SQLException {
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT SupplierPaymentId,PaymentCode,SupplierId,BranchId,PaidBy,PaymentDate,Amount,PaymentMethod,Note " +
                     "FROM purchase.SupplierPayments WHERE SupplierPaymentId=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? PaymentRow.from(rs) : null;
            }
        }
    }

    public List<PaymentRow> listSupplierPayments(Integer supplierId) throws SQLException {
        String sql = "SELECT SupplierPaymentId,PaymentCode,SupplierId,BranchId,PaidBy,PaymentDate,Amount,PaymentMethod,Note " +
                "FROM purchase.SupplierPayments " + (supplierId == null ? "" : "WHERE SupplierId=? ") +
                "ORDER BY PaymentDate DESC,SupplierPaymentId DESC";
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (supplierId != null) ps.setInt(1, supplierId);
            List<PaymentRow> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(PaymentRow.from(rs));
            }
            return rows;
        }
    }

    public BigDecimal getSupplierDebt(int supplierId) throws SQLException {
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DECLARE @approved DECIMAL(18,2); " +
                     "SELECT @approved=COALESCE(SUM(TotalAmount),0) FROM purchase.PurchaseReceipts WHERE SupplierId=? AND Status='APPROVED'; " +
                     "SELECT @approved " +
                     "- COALESCE((SELECT SUM(Amount) FROM purchase.SupplierPayments WHERE SupplierId=?),0) " +
                     "- COALESCE((SELECT SUM(d.QuantityBase*d.UnitCostBase) FROM purchase.SupplierReturns r " +
                     "JOIN purchase.SupplierReturnDetails d ON d.SupplierReturnId=r.SupplierReturnId " +
                     "WHERE r.SupplierId=? AND r.Status='APPROVED'),0) AS Debt")) {
            ps.setInt(1, supplierId);
            ps.setInt(2, supplierId);
            ps.setInt(3, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal("Debt");
            }
        }
    }

    public boolean lotBelongsToSupplier(long lotId, int supplierId, int branchId) throws SQLException {
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM inventory.DrugLots l " +
                     "JOIN purchase.PurchaseReceiptDetails rd ON rd.PurchaseReceiptDetailId=l.PurchaseReceiptDetailId " +
                     "JOIN purchase.PurchaseReceipts r ON r.PurchaseReceiptId=rd.PurchaseReceiptId " +
                     "WHERE l.LotId=? AND r.SupplierId=? AND l.BranchId=? AND r.Status='APPROVED'")) {
            ps.setLong(1, lotId);
            ps.setInt(2, supplierId);
            ps.setInt(3, branchId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean lotExists(long lotId) throws SQLException {
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM inventory.DrugLots WHERE LotId=?")) {
            ps.setLong(1, lotId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public PurchaseRow getPurchaseOrderNoConnection(long id) throws SQLException {
        return getPurchaseOrder(id);
    }

    private List<DetailRow> queryDetails(String sql, long id) throws SQLException {
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            List<DetailRow> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new DetailRow(rs.getLong(1), rs.getLong(2), rs.getInt(3),
                            rs.getBigDecimal(4), rs.getBigDecimal(5)));
                }
            }
            return rows;
        }
    }

    public static class OrderDetail {
        public int drugId;
        public BigDecimal quantity;
        public BigDecimal unitCost;
    }

    public static class ReceiptDetail {
        public int drugId;
        public String lotNumber;
        public LocalDate expiryDate;
        public LocalDate manufactureDate;
        public BigDecimal quantity;
        public BigDecimal unitCost;
    }

    public static class ReturnDetail {
        public long lotId;
        public BigDecimal quantity;
        public BigDecimal unitCost;
    }

    public static class ReturnDetailRow {
        public long id;
        public long returnId;
        public long lotId;
        public BigDecimal quantity;
        public BigDecimal unitCost;
        public static ReturnDetailRow from(ResultSet rs) throws SQLException {
            ReturnDetailRow row = new ReturnDetailRow();
            row.id = rs.getLong("SupplierReturnDetailId");
            row.returnId = rs.getLong("SupplierReturnId");
            row.lotId = rs.getLong("LotId");
            row.quantity = rs.getBigDecimal("QuantityBase");
            row.unitCost = rs.getBigDecimal("UnitCostBase");
            return row;
        }
    }

    public static class PurchaseRow {
        public long id;
        public String code;
        public int supplierId;
        public int branchId;
        public int createdBy;
        public Timestamp orderDate;
        public String status;
        public String note;
        public static PurchaseRow from(ResultSet rs) throws SQLException {
            PurchaseRow r = new PurchaseRow();
            r.id = rs.getLong("PurchaseOrderId");
            r.code = rs.getString("OrderCode");
            r.supplierId = rs.getInt("SupplierId");
            r.branchId = rs.getInt("BranchId");
            r.createdBy = rs.getInt("CreatedBy");
            r.orderDate = rs.getTimestamp("OrderDate");
            r.status = rs.getString("Status");
            r.note = rs.getString("Note");
            return r;
        }
    }

    public static class DetailRow {
        public long id;
        public long purchaseOrderId;
        public int drugId;
        public BigDecimal quantity;
        public BigDecimal unitCost;
        public DetailRow(long id, long purchaseOrderId, int drugId, BigDecimal quantity, BigDecimal unitCost) {
            this.id = id;
            this.purchaseOrderId = purchaseOrderId;
            this.drugId = drugId;
            this.quantity = quantity;
            this.unitCost = unitCost;
        }
    }

    public static class ReceiptRow {
        public long id;
        public String code;
        public Long purchaseOrderId;
        public int supplierId;
        public int branchId;
        public int createdBy;
        public Integer approvedBy;
        public Timestamp receiptDate;
        public String status;
        public BigDecimal totalAmount;
        public String note;
        public static ReceiptRow from(ResultSet rs) throws SQLException {
            ReceiptRow r = new ReceiptRow();
            r.id = rs.getLong("PurchaseReceiptId");
            r.code = rs.getString("ReceiptCode");
            long po = rs.getLong("PurchaseOrderId");
            r.purchaseOrderId = rs.wasNull() ? null : po;
            r.supplierId = rs.getInt("SupplierId");
            r.branchId = rs.getInt("BranchId");
            r.createdBy = rs.getInt("CreatedBy");
            int approved = rs.getInt("ApprovedBy");
            r.approvedBy = rs.wasNull() ? null : approved;
            r.receiptDate = rs.getTimestamp("ReceiptDate");
            r.status = rs.getString("Status");
            r.totalAmount = rs.getBigDecimal("TotalAmount");
            r.note = rs.getString("Note");
            return r;
        }
    }

    public static class ReceiptDetailRow {
        public long id;
        public long receiptId;
        public int drugId;
        public String lotNumber;
        public Date expiryDate;
        public Date manufactureDate;
        public BigDecimal quantity;
        public BigDecimal unitCost;
        public static ReceiptDetailRow from(ResultSet rs) throws SQLException {
            ReceiptDetailRow r = new ReceiptDetailRow();
            r.id = rs.getLong("PurchaseReceiptDetailId");
            r.receiptId = rs.getLong("PurchaseReceiptId");
            r.drugId = rs.getInt("DrugId");
            r.lotNumber = rs.getString("LotNumber");
            r.expiryDate = rs.getDate("ExpiryDate");
            r.manufactureDate = rs.getDate("ManufactureDate");
            r.quantity = rs.getBigDecimal("QuantityBase");
            r.unitCost = rs.getBigDecimal("UnitCostBase");
            return r;
        }
    }

    public static class ReturnRow {
        public long id;
        public String code;
        public int supplierId;
        public int branchId;
        public int createdBy;
        public Timestamp returnDate;
        public String status;
        public String reason;
        public static ReturnRow from(ResultSet rs) throws SQLException {
            ReturnRow r = new ReturnRow();
            r.id = rs.getLong("SupplierReturnId");
            r.code = rs.getString("ReturnCode");
            r.supplierId = rs.getInt("SupplierId");
            r.branchId = rs.getInt("BranchId");
            r.createdBy = rs.getInt("CreatedBy");
            r.returnDate = rs.getTimestamp("ReturnDate");
            r.status = rs.getString("Status");
            r.reason = rs.getString("Reason");
            return r;
        }
    }

    public static class PaymentRow {
        public long id;
        public String code;
        public int supplierId;
        public Integer branchId;
        public int paidBy;
        public Timestamp paymentDate;
        public BigDecimal amount;
        public String paymentMethod;
        public String note;
        public static PaymentRow from(ResultSet rs) throws SQLException {
            PaymentRow r = new PaymentRow();
            r.id = rs.getLong("SupplierPaymentId");
            r.code = rs.getString("PaymentCode");
            r.supplierId = rs.getInt("SupplierId");
            int branch = rs.getInt("BranchId");
            r.branchId = rs.wasNull() ? null : branch;
            r.paidBy = rs.getInt("PaidBy");
            r.paymentDate = rs.getTimestamp("PaymentDate");
            r.amount = rs.getBigDecimal("Amount");
            r.paymentMethod = rs.getString("PaymentMethod");
            r.note = rs.getString("Note");
            return r;
        }
    }
}
