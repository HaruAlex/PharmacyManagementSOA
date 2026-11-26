package repositories;

import models.SalesReturn;
import play.db.Database;

import javax.inject.Inject;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * SalesReturnRepository - JDBC Repository cho phiếu trả hàng
 * 
 * Database Table: sales.SalesReturns
 * - salesReturnId (PK)
 * - returnCode (UNIQUE)
 * - invoiceId (FK)
 * - createdBy (FK)
 * - returnDate
 * - status (DRAFT, APPROVED, CANCELLED)
 * - returnReasonCode
 * - note
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class SalesReturnRepository {
    
    private final Database database;
    
    @Inject
    public SalesReturnRepository(Database database) {
        this.database = database;
    }
    
    /**
     * Lấy phiếu trả theo ID
     */
    public Optional<SalesReturn> findById(Long salesReturnId) {
        String sql = "SELECT * FROM sales.SalesReturns WHERE SalesReturnId = ?";
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, salesReturnId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToSalesReturn(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }
    
    /**
     * Lấy phiếu trả theo mã phiếu
     */
    public Optional<SalesReturn> findByReturnCode(String returnCode) {
        String sql = "SELECT * FROM sales.SalesReturns WHERE ReturnCode = ?";
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, returnCode);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToSalesReturn(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }
    
    /**
     * Lấy tất cả phiếu trả của một hóa đơn
     */
    public List<SalesReturn> findByInvoiceId(Long invoiceId) {
        String sql = "SELECT * FROM sales.SalesReturns WHERE InvoiceId = ? ORDER BY ReturnDate DESC";
        List<SalesReturn> returns = new ArrayList<>();
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, invoiceId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    returns.add(mapRowToSalesReturn(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return returns;
    }
    
    /**
     * Lấy tất cả phiếu trả của một user
     */
    public List<SalesReturn> findByCreatedBy(Long userId) {
        String sql = "SELECT * FROM sales.SalesReturns WHERE CreatedBy = ? ORDER BY ReturnDate DESC";
        List<SalesReturn> returns = new ArrayList<>();
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    returns.add(mapRowToSalesReturn(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return returns;
    }
    
    /**
     * Lấy tất cả phiếu trả có status = DRAFT
     */
    public List<SalesReturn> findByStatusDraft() {
        String sql = "SELECT * FROM sales.SalesReturns WHERE Status = 'DRAFT' ORDER BY ReturnDate DESC";
        List<SalesReturn> returns = new ArrayList<>();
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    returns.add(mapRowToSalesReturn(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return returns;
    }
    
    /**
     * Thêm phiếu trả mới
     */
    public Long create(SalesReturn salesReturn) {
        String sql = "INSERT INTO sales.SalesReturns " +
                "(ReturnCode, InvoiceId, CreatedBy, ReturnDate, Status, ReturnReasonCode, Note) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, salesReturn.getReturnCode());
            stmt.setLong(2, salesReturn.getInvoiceId());
            stmt.setLong(3, salesReturn.getCreatedBy());
            stmt.setTimestamp(4, Timestamp.valueOf(salesReturn.getReturnDate()));
            stmt.setString(5, salesReturn.getStatus().toString());
            stmt.setString(6, salesReturn.getReturnReasonCode());
            stmt.setString(7, salesReturn.getNote());
            
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
     * Cập nhật phiếu trả
     */
    public boolean update(SalesReturn salesReturn) {
        String sql = "UPDATE sales.SalesReturns SET " +
                "Status = ?, ReturnReasonCode = ?, Note = ? " +
                "WHERE SalesReturnId = ?";
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, salesReturn.getStatus().toString());
            stmt.setString(2, salesReturn.getReturnReasonCode());
            stmt.setString(3, salesReturn.getNote());
            stmt.setLong(4, salesReturn.getSalesReturnId());
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    /**
     * Xóa phiếu trả
     */
    public boolean delete(Long salesReturnId) {
        String sql = "DELETE FROM sales.SalesReturns WHERE SalesReturnId = ?";
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, salesReturnId);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    /**
     * Lấy số lượng phiếu trả của một hóa đơn
     */
    public int getCountByInvoiceId(Long invoiceId) {
        String sql = "SELECT COUNT(*) as count FROM sales.SalesReturns WHERE InvoiceId = ?";
        
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
     * Map dòng ResultSet thành object SalesReturn
     */
    private SalesReturn mapRowToSalesReturn(ResultSet rs) throws SQLException {
        SalesReturn salesReturn = new SalesReturn();
        salesReturn.setSalesReturnId(rs.getLong("SalesReturnId"));
        salesReturn.setReturnCode(rs.getString("ReturnCode"));
        salesReturn.setInvoiceId(rs.getLong("InvoiceId"));
        salesReturn.setCreatedBy(rs.getLong("CreatedBy"));
        
        Timestamp returnDate = rs.getTimestamp("ReturnDate");
        if (returnDate != null) {
            salesReturn.setReturnDate(returnDate.toLocalDateTime());
        }
        
        String status = rs.getString("Status");
        if (status != null) {
            salesReturn.setStatus(SalesReturn.SalesReturnStatus.valueOf(status));
        }
        
        salesReturn.setReturnReasonCode(rs.getString("ReturnReasonCode"));
        salesReturn.setNote(rs.getString("Note"));
        
        return salesReturn;
    }
}
