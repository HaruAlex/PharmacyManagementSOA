package repositories;

import models.InvoiceDetailLot;
import play.db.Database;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * InvoiceDetailLotRepository - JDBC repository cho InvoiceDetailLot
 * 
 * Quản lý: chi tiết dòng hóa đơn gồm lot (nhóm thuốc từ cùng 1 lot)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class InvoiceDetailLotRepository {
    
    private final Database db;
    
    @Inject
    public InvoiceDetailLotRepository(Database db) {
        this.db = db;
    }
    
    /**
     * Lấy chi tiết lot theo ID
     */
    public Optional<InvoiceDetailLot> findById(Long id) {
        return db.withConnection(conn -> {
            String sql = "SELECT InvoiceDetailLotId, InvoiceDetailId, LotId, QuantityBase, CostBaseAtSale " +
                         "FROM sales.InvoiceDetailLots WHERE InvoiceDetailLotId = ?";
            
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, id);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRowToInvoiceDetailLot(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }
    
    /**
     * Lấy chi tiết lots của invoice detail
     */
    public List<InvoiceDetailLot> findByInvoiceDetailId(Long invoiceDetailId) {
        return db.withConnection(conn -> {
            String sql = "SELECT InvoiceDetailLotId, InvoiceDetailId, LotId, QuantityBase, CostBaseAtSale " +
                         "FROM sales.InvoiceDetailLots WHERE InvoiceDetailId = ?";
            
            List<InvoiceDetailLot> lots = new java.util.ArrayList<>();
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, invoiceDetailId);
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        lots.add(mapRowToInvoiceDetailLot(rs));
                    }
                }
            }
            return lots;
        });
    }
    
    /**
     * Tính tổng cost từ list invoice IDs
     * 
     * @param invoiceIds List ID hóa đơn
     * @return Tổng cost = SUM(CostBaseAtSale * QuantityBase)
     */
    public BigDecimal getTotalCostByInvoiceIds(List<Long> invoiceIds) {
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        return db.withConnection(conn -> {
            // Build IN clause: (?,?,?)
            String placeholders = String.join(",", java.util.Collections.nCopies(invoiceIds.size(), "?"));
            String sql = "SELECT SUM(CAST(idl.CostBaseAtSale AS DECIMAL(18,2)) * CAST(idl.QuantityBase AS DECIMAL(18,2))) as TotalCost " +
                         "FROM sales.InvoiceDetailLots idl " +
                         "JOIN sales.InvoiceDetails id ON idl.InvoiceDetailId = id.InvoiceDetailId " +
                         "WHERE id.InvoiceId IN (" + placeholders + ")";
            
            try (var stmt = conn.prepareStatement(sql)) {
                // Set parameters
                for (int i = 0; i < invoiceIds.size(); i++) {
                    stmt.setLong(i + 1, invoiceIds.get(i));
                }
                
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        BigDecimal total = rs.getBigDecimal("TotalCost");
                        return total != null ? total : BigDecimal.ZERO;
                    }
                }
            }
            return BigDecimal.ZERO;
        });
    }
    
    /**
     * Tạo invoice detail lot mới
     */
    public Long create(InvoiceDetailLot lot) {
        return db.withConnection(conn -> {
            String sql = "INSERT INTO sales.InvoiceDetailLots " +
                         "(InvoiceDetailId, LotId, QuantityBase, CostBaseAtSale) " +
                         "VALUES (?, ?, ?, ?); " +
                         "SELECT CAST(SCOPE_IDENTITY() AS BIGINT)";
            
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, lot.getInvoiceDetailId());
                stmt.setLong(2, lot.getLotId());
                stmt.setBigDecimal(3, lot.getQuantityBase());
                stmt.setBigDecimal(4, lot.getCostBaseAtSale());
                
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Long id = rs.getLong(1);
                        lot.setInvoiceDetailLotId(id);
                        return id;
                    }
                }
            }
            throw new RuntimeException("Failed to create InvoiceDetailLot - no generated ID");
        });
    }
    
    /**
     * Cập nhật invoice detail lot
     */
    public boolean update(InvoiceDetailLot lot) {
        return db.withConnection(conn -> {
            String sql = "UPDATE sales.InvoiceDetailLots " +
                         "SET QuantityBase = ?, CostBaseAtSale = ? " +
                         "WHERE InvoiceDetailLotId = ?";
            
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setBigDecimal(1, lot.getQuantityBase());
                stmt.setBigDecimal(2, lot.getCostBaseAtSale());
                stmt.setLong(3, lot.getInvoiceDetailLotId());
                
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            }
        });
    }
    
    /**
     * Xóa invoice detail lot
     */
    public boolean delete(Long id) {
        return db.withConnection(conn -> {
            String sql = "DELETE FROM sales.InvoiceDetailLots WHERE InvoiceDetailLotId = ?";
            
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, id);
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            }
        });
    }
    
    /**
     * Xóa tất cả lots của invoice detail
     */
    public int deleteByInvoiceDetailId(Long invoiceDetailId) {
        return db.withConnection(conn -> {
            String sql = "DELETE FROM sales.InvoiceDetailLots WHERE InvoiceDetailId = ?";
            
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, invoiceDetailId);
                return stmt.executeUpdate();
            }
        });
    }
    
    /**
     * Map ResultSet row to InvoiceDetailLot object
     */
    private InvoiceDetailLot mapRowToInvoiceDetailLot(java.sql.ResultSet rs) throws java.sql.SQLException {
        InvoiceDetailLot lot = new InvoiceDetailLot();
        lot.setInvoiceDetailLotId(rs.getLong("InvoiceDetailLotId"));
        lot.setInvoiceDetailId(rs.getLong("InvoiceDetailId"));
        lot.setLotId(rs.getLong("LotId"));
        lot.setQuantityBase(rs.getBigDecimal("QuantityBase"));
        lot.setCostBaseAtSale(rs.getBigDecimal("CostBaseAtSale"));
        return lot;
    }
}
