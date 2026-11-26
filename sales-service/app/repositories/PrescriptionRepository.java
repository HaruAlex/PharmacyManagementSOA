package repositories;

import models.Prescription;
import play.db.Database;

import javax.inject.Inject;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * PrescriptionRepository - JDBC Repository cho đơn thuốc
 * 
 * Database Table: sales.Prescriptions
 * - prescriptionId (PK)
 * - prescriptionCode (UNIQUE)
 * - customerId (FK, nullable)
 * - createdBy (FK)
 * - prescriptionDate
 * - prescriberName
 * - facilityName
 * - notes
 * - createdAt
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class PrescriptionRepository {
    
    private final Database database;
    
    @Inject
    public PrescriptionRepository(Database database) {
        this.database = database;
    }
    
    /**
     * Lấy đơn thuốc theo ID
     */
    public Optional<Prescription> findById(Long prescriptionId) {
        String sql = "SELECT * FROM sales.Prescriptions WHERE PrescriptionId = ?";
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, prescriptionId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToPrescription(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }
    
    /**
     * Lấy đơn thuốc theo mã đơn
     */
    public Optional<Prescription> findByCode(String prescriptionCode) {
        String sql = "SELECT * FROM sales.Prescriptions WHERE PrescriptionCode = ?";
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, prescriptionCode);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToPrescription(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }
    
    /**
     * Lấy tất cả đơn thuốc của một khách hàng
     */
    public List<Prescription> findByCustomerId(Long customerId) {
        String sql = "SELECT * FROM sales.Prescriptions WHERE CustomerId = ? ORDER BY PrescriptionDate DESC";
        List<Prescription> prescriptions = new ArrayList<>();
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    prescriptions.add(mapRowToPrescription(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return prescriptions;
    }
    
    /**
     * Lấy đơn thuốc theo ngày
     */
    public List<Prescription> findByPrescriptionDate(LocalDate date) {
        String sql = "SELECT * FROM sales.Prescriptions WHERE PrescriptionDate = ? ORDER BY CreatedAt DESC";
        List<Prescription> prescriptions = new ArrayList<>();
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setDate(1, java.sql.Date.valueOf(date));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    prescriptions.add(mapRowToPrescription(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return prescriptions;
    }
    
    /**
     * Lấy đơn thuốc theo người lập
     */
    public List<Prescription> findByCreatedBy(Long userId) {
        String sql = "SELECT * FROM sales.Prescriptions WHERE CreatedBy = ? ORDER BY CreatedAt DESC";
        List<Prescription> prescriptions = new ArrayList<>();
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    prescriptions.add(mapRowToPrescription(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return prescriptions;
    }
    
    /**
     * Thêm đơn thuốc mới
     */
    public Long create(Prescription prescription) {
        String sql = "INSERT INTO sales.Prescriptions " +
                "(PrescriptionCode, CustomerId, PrescriberName, FacilityName, PrescriptionDate, Notes, CreatedBy, CreatedAt) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, prescription.getPrescriptionCode());
            if (prescription.getCustomerId() != null) {
                stmt.setLong(2, prescription.getCustomerId());
            } else {
                stmt.setNull(2, java.sql.Types.BIGINT);
            }
            stmt.setString(3, prescription.getPrescriberName());
            stmt.setString(4, prescription.getFacilityName());
            stmt.setDate(5, java.sql.Date.valueOf(prescription.getPrescriptionDate()));
            stmt.setString(6, prescription.getNotes());
            stmt.setLong(7, prescription.getCreatedBy());
            stmt.setTimestamp(8, Timestamp.valueOf(prescription.getCreatedAt()));
            
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
     * Cập nhật đơn thuốc
     */
    public boolean update(Prescription prescription) {
        String sql = "UPDATE sales.Prescriptions SET " +
                "Notes = ?, FacilityName = ? " +
                "WHERE PrescriptionId = ?";
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, prescription.getNotes());
            stmt.setString(2, prescription.getFacilityName());
            stmt.setLong(3, prescription.getPrescriptionId());
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    /**
     * Xóa đơn thuốc
     */
    public boolean delete(Long prescriptionId) {
        String sql = "DELETE FROM sales.Prescriptions WHERE PrescriptionId = ?";
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, prescriptionId);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    /**
     * Lấy số lượng đơn thuốc của một khách hàng
     */
    public int getCountByCustomerId(Long customerId) {
        String sql = "SELECT COUNT(*) as count FROM sales.Prescriptions WHERE CustomerId = ?";
        
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, customerId);
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
     * Map dòng ResultSet thành object Prescription
     */
    private Prescription mapRowToPrescription(ResultSet rs) throws SQLException {
        Prescription prescription = new Prescription();
        prescription.setPrescriptionId(rs.getLong("PrescriptionId"));
        prescription.setPrescriptionCode(rs.getString("PrescriptionCode"));
        
        Long customerId = rs.getLong("CustomerId");
        if (customerId > 0) {
            prescription.setCustomerId(customerId);
        }
        
        prescription.setPrescriberName(rs.getString("PrescriberName"));
        prescription.setFacilityName(rs.getString("FacilityName"));
        
        java.sql.Date prescriptionDate = rs.getDate("PrescriptionDate");
        if (prescriptionDate != null) {
            prescription.setPrescriptionDate(prescriptionDate.toLocalDate());
        }
        
        prescription.setNotes(rs.getString("Notes"));
        prescription.setCreatedBy(rs.getLong("CreatedBy"));
        
        Timestamp createdAt = rs.getTimestamp("CreatedAt");
        if (createdAt != null) {
            prescription.setCreatedAt(createdAt.toLocalDateTime());
        }
        
        return prescription;
    }
}
