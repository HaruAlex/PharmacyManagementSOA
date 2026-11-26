package repositories;

import models.InvoiceDetail;
import play.db.Database;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/**
 * InvoiceDetailRepository - JDBC Repository cho chi tiết hóa đơn
 * 
 * Database Table: sales.InvoiceDetails
 * - invoiceDetailId (PK)
 * - invoiceId (FK)
 * - drugId (FK)
 * - unitId (FK)
 * - quantity
 * - quantityBase
 * - unitPrice
 * - discountAmount
 * - lineTotal
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class InvoiceDetailRepository {
    
    private final Database database;
    
    @Inject
    public InvoiceDetailRepository(Database database) {
        this.database = database;
    }
    
    /**
     * Lấy chi tiết hóa đơn theo ID
     */
    public Optional<InvoiceDetail> findById(Long invoiceDetailId) {
        String sql = "SELECT * FROM sales.InvoiceDetails WHERE InvoiceDetailId = ?";
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, invoiceDetailId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToInvoiceDetail(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }
    
    /**
     * Lấy tất cả chi tiết của một hóa đơn
     */
    public List<InvoiceDetail> findByInvoiceId(Long invoiceId) {
        String sql = "SELECT * FROM sales.InvoiceDetails WHERE InvoiceId = ? ORDER BY InvoiceDetailId";
        List<InvoiceDetail> details = new ArrayList<>();
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, invoiceId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    details.add(mapRowToInvoiceDetail(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return details;
    }
    
    /**
     * Thêm chi tiết hóa đơn mới
     */
    public Long create(InvoiceDetail detail) {
        String sql = "INSERT INTO sales.InvoiceDetails " +
                "(InvoiceId, DrugId, UnitId, Quantity, QuantityBase, UnitPrice, DiscountAmount, LineTotal) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setLong(1, detail.getInvoiceId());
            stmt.setLong(2, detail.getDrugId());
            stmt.setLong(3, detail.getUnitId());
            stmt.setBigDecimal(4, detail.getQuantity());
            stmt.setBigDecimal(5, detail.getQuantityBase());
            stmt.setBigDecimal(6, detail.getUnitPrice());
            stmt.setBigDecimal(7, detail.getDiscountAmount() != null ? detail.getDiscountAmount() : BigDecimal.ZERO);
            stmt.setBigDecimal(8, detail.getLineTotal());
            
            stmt.executeUpdate();
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Cập nhật chi tiết hóa đơn
     */
    public boolean update(InvoiceDetail detail) {
        String sql = "UPDATE sales.InvoiceDetails SET " +
                "Quantity = ?, QuantityBase = ?, UnitPrice = ?, DiscountAmount = ?, LineTotal = ? " +
                "WHERE InvoiceDetailId = ?";
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setBigDecimal(1, detail.getQuantity());
            stmt.setBigDecimal(2, detail.getQuantityBase());
            stmt.setBigDecimal(3, detail.getUnitPrice());
            stmt.setBigDecimal(4, detail.getDiscountAmount() != null ? detail.getDiscountAmount() : BigDecimal.ZERO);
            stmt.setBigDecimal(5, detail.getLineTotal());
            stmt.setLong(6, detail.getInvoiceDetailId());
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    /**
     * Xóa chi tiết hóa đơn
     */
    public boolean delete(Long invoiceDetailId) {
        String sql = "DELETE FROM sales.InvoiceDetails WHERE InvoiceDetailId = ?";
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, invoiceDetailId);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    /**
     * Lấy tổng số tiền của các chi tiết trong một hóa đơn
     */
    public BigDecimal getTotalByInvoiceId(Long invoiceId) {
        String sql = "SELECT COALESCE(SUM(LineTotal), 0) as total FROM sales.InvoiceDetails WHERE InvoiceId = ?";
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, invoiceId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("total");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return BigDecimal.ZERO;
    }
    
    /**
     * Lấy số lượng chi tiết trong một hóa đơn
     */
    public int getCountByInvoiceId(Long invoiceId) {
        String sql = "SELECT COUNT(*) as count FROM sales.InvoiceDetails WHERE InvoiceId = ?";
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, invoiceId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
    
    /**
     * Xóa tất cả chi tiết của một hóa đơn
     */
    public int deleteByInvoiceId(Long invoiceId) {
        String sql = "DELETE FROM sales.InvoiceDetails WHERE InvoiceId = ?";
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, invoiceId);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
    
    /**
     * Map dòng ResultSet thành object InvoiceDetail
     */
    private InvoiceDetail mapRowToInvoiceDetail(ResultSet rs) throws SQLException {
        InvoiceDetail detail = new InvoiceDetail();
        detail.setInvoiceDetailId(rs.getLong("InvoiceDetailId"));
        detail.setInvoiceId(rs.getLong("InvoiceId"));
        detail.setDrugId(rs.getLong("DrugId"));
        detail.setUnitId(rs.getLong("UnitId"));
        detail.setQuantity(rs.getBigDecimal("Quantity"));
        detail.setQuantityBase(rs.getBigDecimal("QuantityBase"));
        detail.setUnitPrice(rs.getBigDecimal("UnitPrice"));
        detail.setDiscountAmount(rs.getBigDecimal("DiscountAmount"));
        detail.setLineTotal(rs.getBigDecimal("LineTotal"));
        return detail;
    }
}
