package repositories;

import models.Invoice;
import models.Invoice.InvoiceStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import play.db.Database;

/**
 * InvoiceRepository - JDBC Data Access Layer cho Invoice (Hóa đơn)
 * 
 * Database Table: sales.Invoices
 * - InvoiceId BIGINT PK IDENTITY(1,1)
 * - InvoiceCode VARCHAR(30) UNIQUE
 * - BranchId INT (FK → catalog.Branches)
 * - UserId INT (FK → auth.Users)
 * - ShiftId BIGINT (FK → sales.Shifts, nullable)
 * - CustomerId INT (FK → catalog.Customers, nullable)
 * - PrescriptionId BIGINT (FK → sales.Prescriptions, nullable)
 * - InvoiceDate DATETIME2
 * - Status VARCHAR(20): DRAFT, PROCESSING, COMPLETED, CANCELLED
 * - Subtotal, DiscountAmount, TotalAmount DECIMAL(18,2)
 * - PaymentMethod VARCHAR(20): CASH, BANK_TRANSFER, CARD (nullable)
 * - Note NVARCHAR(300) (nullable)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class InvoiceRepository {
    
    private final Database db;
    
    @Inject
    public InvoiceRepository(Database db) {
        this.db = db;
    }
    
    /**
     * Lấy invoice theo ID
     */
    public Optional<Invoice> findById(Long invoiceId) {
        return db.withConnection(conn -> {
            String sql = "SELECT InvoiceId, InvoiceCode, BranchId, UserId, ShiftId, " +
                         "CustomerId, PrescriptionId, InvoiceDate, Status, Subtotal, " +
                         "DiscountAmount, TotalAmount, PaymentMethod, Note " +
                         "FROM sales.Invoices WHERE InvoiceId = ?";
            
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, invoiceId);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRowToInvoice(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }
    
    /**
     * Lấy invoice theo code (unique)
     */
    public Optional<Invoice> findByCode(String invoiceCode) {
        return db.withConnection(conn -> {
            String sql = "SELECT InvoiceId, InvoiceCode, BranchId, UserId, ShiftId, " +
                         "CustomerId, PrescriptionId, InvoiceDate, Status, Subtotal, " +
                         "DiscountAmount, TotalAmount, PaymentMethod, Note " +
                         "FROM sales.Invoices WHERE InvoiceCode = ?";
            
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, invoiceCode);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRowToInvoice(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }
    
    /**
     * Lấy invoices của customer
     */
    public List<Invoice> findByCustomerId(Long customerId, int page, int pageSize) {
        return db.withConnection(conn -> {
            int offset = (page - 1) * pageSize;
            String sql = "SELECT InvoiceId, InvoiceCode, BranchId, UserId, ShiftId, " +
                         "CustomerId, PrescriptionId, InvoiceDate, Status, Subtotal, " +
                         "DiscountAmount, TotalAmount, PaymentMethod, Note " +
                         "FROM sales.Invoices WHERE CustomerId = ? " +
                         "ORDER BY InvoiceDate DESC " +
                         "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
            
            List<Invoice> invoices = new java.util.ArrayList<>();
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, customerId);
                stmt.setInt(2, offset);
                stmt.setInt(3, pageSize);
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        invoices.add(mapRowToInvoice(rs));
                    }
                }
            }
            return invoices;
        });
    }
    
    /**
     * Lấy invoices theo status
     */
    public List<Invoice> findByStatus(InvoiceStatus status, int page, int pageSize) {
        return db.withConnection(conn -> {
            int offset = (page - 1) * pageSize;
            String sql = "SELECT InvoiceId, InvoiceCode, BranchId, UserId, ShiftId, " +
                         "CustomerId, PrescriptionId, InvoiceDate, Status, Subtotal, " +
                         "DiscountAmount, TotalAmount, PaymentMethod, Note " +
                         "FROM sales.Invoices WHERE Status = ? " +
                         "ORDER BY InvoiceDate DESC " +
                         "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
            
            List<Invoice> invoices = new java.util.ArrayList<>();
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, status.name());
                stmt.setInt(2, offset);
                stmt.setInt(3, pageSize);
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        invoices.add(mapRowToInvoice(rs));
                    }
                }
            }
            return invoices;
        });
    }
    
    /**
     * Lấy invoices theo chi nhánh và ngày
     */
    public List<Invoice> findByBranchAndDate(Long branchId, LocalDate date) {
        return db.withConnection(conn -> {
            String sql = "SELECT InvoiceId, InvoiceCode, BranchId, UserId, ShiftId, " +
                         "CustomerId, PrescriptionId, InvoiceDate, Status, Subtotal, " +
                         "DiscountAmount, TotalAmount, PaymentMethod, Note " +
                         "FROM sales.Invoices WHERE BranchId = ? AND CAST(InvoiceDate AS DATE) = ? " +
                         "ORDER BY InvoiceDate DESC";
            
            List<Invoice> invoices = new java.util.ArrayList<>();
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, branchId);
                stmt.setDate(2, java.sql.Date.valueOf(date));
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        invoices.add(mapRowToInvoice(rs));
                    }
                }
            }
            return invoices;
        });
    }
    
    /**
     * Lấy invoices theo user và ngày
     */
    public List<Invoice> findByUserAndDate(Long userId, LocalDate date) {
        return db.withConnection(conn -> {
            String sql = "SELECT InvoiceId, InvoiceCode, BranchId, UserId, ShiftId, " +
                         "CustomerId, PrescriptionId, InvoiceDate, Status, Subtotal, " +
                         "DiscountAmount, TotalAmount, PaymentMethod, Note " +
                         "FROM sales.Invoices WHERE UserId = ? AND CAST(InvoiceDate AS DATE) = ? " +
                         "ORDER BY InvoiceDate DESC";
            
            List<Invoice> invoices = new java.util.ArrayList<>();
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, userId);
                stmt.setDate(2, java.sql.Date.valueOf(date));
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        invoices.add(mapRowToInvoice(rs));
                    }
                }
            }
            return invoices;
        });
    }
    
    /**
     * Lấy invoices theo shift
     */
    public List<Invoice> findByShiftId(Long shiftId) {
        return db.withConnection(conn -> {
            String sql = "SELECT InvoiceId, InvoiceCode, BranchId, UserId, ShiftId, " +
                         "CustomerId, PrescriptionId, InvoiceDate, Status, Subtotal, " +
                         "DiscountAmount, TotalAmount, PaymentMethod, Note " +
                         "FROM sales.Invoices WHERE ShiftId = ? " +
                         "ORDER BY InvoiceDate DESC";
            
            List<Invoice> invoices = new java.util.ArrayList<>();
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, shiftId);
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        invoices.add(mapRowToInvoice(rs));
                    }
                }
            }
            return invoices;
        });
    }
    
    /**
     * Lấy top users theo revenue trong ngày
     */
    public List<Object[]> getTopUsersByRevenueAndDate(Long branchId, LocalDate date, int limit) {
        return db.withConnection(conn -> {
            String sql = "SELECT TOP (?) i.UserId, SUM(i.TotalAmount) as TotalRevenue " +
                         "FROM sales.Invoices i " +
                         "WHERE i.BranchId = ? AND CAST(i.InvoiceDate AS DATE) = ? " +
                         "AND i.Status = 'COMPLETED' " +
                         "GROUP BY i.UserId " +
                         "ORDER BY TotalRevenue DESC";
            
            List<Object[]> results = new java.util.ArrayList<>();
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, limit);
                stmt.setLong(2, branchId);
                stmt.setDate(3, java.sql.Date.valueOf(date));
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Object[] row = new Object[2];
                        row[0] = rs.getLong("UserId");
                        row[1] = rs.getBigDecimal("TotalRevenue");
                        results.add(row);
                    }
                }
            }
            return results;
        });
    }
    
    /**
     * Tạo invoice mới
     * INSERT và trả về InvoiceId
     */
    public Long create(Invoice invoice) {
        return db.withConnection(conn -> {
            String sql = "INSERT INTO sales.Invoices " +
                         "(InvoiceCode, BranchId, UserId, ShiftId, CustomerId, PrescriptionId, " +
                         "InvoiceDate, Status, Subtotal, DiscountAmount, TotalAmount, PaymentMethod, Note) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?); " +
                         "SELECT CAST(SCOPE_IDENTITY() AS BIGINT)";
            
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, invoice.getInvoiceCode());
                stmt.setLong(2, invoice.getBranchId());
                stmt.setLong(3, invoice.getUserId());
                
                if (invoice.getShiftId() != null) {
                    stmt.setLong(4, invoice.getShiftId());
                } else {
                    stmt.setNull(4, java.sql.Types.BIGINT);
                }
                
                if (invoice.getCustomerId() != null) {
                    stmt.setLong(5, invoice.getCustomerId());
                } else {
                    stmt.setNull(5, java.sql.Types.BIGINT);
                }
                
                if (invoice.getPrescriptionId() != null) {
                    stmt.setLong(6, invoice.getPrescriptionId());
                } else {
                    stmt.setNull(6, java.sql.Types.BIGINT);
                }
                
                stmt.setObject(7, invoice.getInvoiceDate());
                stmt.setString(8, invoice.getStatus().name());
                stmt.setBigDecimal(9, invoice.getSubtotal());
                stmt.setBigDecimal(10, invoice.getDiscountAmount());
                stmt.setBigDecimal(11, invoice.getTotalAmount());
                
                if (invoice.getPaymentMethod() != null) {
                    stmt.setString(12, invoice.getPaymentMethod().name());
                } else {
                    stmt.setNull(12, java.sql.Types.VARCHAR);
                }
                
                if (invoice.getNote() != null) {
                    stmt.setString(13, invoice.getNote());
                } else {
                    stmt.setNull(13, java.sql.Types.VARCHAR);
                }
                
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Long invoiceId = rs.getLong(1);
                        invoice.setInvoiceId(invoiceId);
                        return invoiceId;
                    }
                }
            }
            throw new RuntimeException("Failed to create Invoice - no generated ID");
        });
    }
    
    /**
     * Cập nhật invoice
     */
    public boolean update(Invoice invoice) {
        return db.withConnection(conn -> {
            String sql = "UPDATE sales.Invoices SET " +
                         "Status = ?, Subtotal = ?, DiscountAmount = ?, TotalAmount = ?, " +
                         "PaymentMethod = ?, Note = ? WHERE InvoiceId = ?";
            
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, invoice.getStatus().name());
                stmt.setBigDecimal(2, invoice.getSubtotal());
                stmt.setBigDecimal(3, invoice.getDiscountAmount());
                stmt.setBigDecimal(4, invoice.getTotalAmount());
                
                if (invoice.getPaymentMethod() != null) {
                    stmt.setString(5, invoice.getPaymentMethod().name());
                } else {
                    stmt.setNull(5, java.sql.Types.VARCHAR);
                }
                
                if (invoice.getNote() != null) {
                    stmt.setString(6, invoice.getNote());
                } else {
                    stmt.setNull(6, java.sql.Types.VARCHAR);
                }
                
                stmt.setLong(7, invoice.getInvoiceId());
                
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            }
        });
    }
    
    /**
     * Cancel invoice - set Status = CANCELLED + cancelledBy + cancelledAt + cancelReason
     * Dùng khi hủy hóa đơn
     */
    /**
     * Cancel an invoice by setting status to CANCELLED
     */
    public boolean cancel(Long invoiceId) {
        return cancel(invoiceId, InvoiceStatus.COMPLETED);
    }

    public boolean cancel(Long invoiceId, InvoiceStatus expectedStatus) {
        return db.withConnection(conn -> {
            String sql = "UPDATE sales.Invoices SET Status = 'CANCELLED' " +
                    "WHERE InvoiceId = ? AND Status = ?";
            
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, invoiceId);
                stmt.setString(2, expectedStatus.name());
                
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            }
        });
    }

    public boolean beginProcessing(Long invoiceId) {
        return db.withConnection(conn -> {
            String sql = "UPDATE sales.Invoices SET Status = 'PROCESSING' " +
                    "WHERE InvoiceId = ? AND Status = 'DRAFT'";
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, invoiceId);
                return stmt.executeUpdate() > 0;
            }
        });
    }

    public boolean completeProcessing(Long invoiceId) {
        return db.withConnection(conn -> {
            String sql = "UPDATE sales.Invoices SET Status = 'COMPLETED' " +
                    "WHERE InvoiceId = ? AND Status = 'PROCESSING'";
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, invoiceId);
                return stmt.executeUpdate() > 0;
            }
        });
    }

    public boolean returnToDraft(Long invoiceId) {
        return db.withConnection(conn -> {
            String sql = "UPDATE sales.Invoices SET Status = 'DRAFT' " +
                    "WHERE InvoiceId = ? AND Status = 'PROCESSING'";
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, invoiceId);
                return stmt.executeUpdate() > 0;
            }
        });
    }
    
    /**
     * Map ResultSet row to Invoice object
     */
    private Invoice mapRowToInvoice(java.sql.ResultSet rs) throws java.sql.SQLException {
        Invoice invoice = new Invoice();
        invoice.setInvoiceId(rs.getLong("InvoiceId"));
        invoice.setInvoiceCode(rs.getString("InvoiceCode"));
        invoice.setBranchId(rs.getLong("BranchId"));
        invoice.setUserId(rs.getLong("UserId"));
        
        if (rs.getObject("ShiftId") != null) {
            invoice.setShiftId(rs.getLong("ShiftId"));
        }
        
        if (rs.getObject("CustomerId") != null) {
            invoice.setCustomerId(rs.getLong("CustomerId"));
        }
        
        if (rs.getObject("PrescriptionId") != null) {
            invoice.setPrescriptionId(rs.getLong("PrescriptionId"));
        }
        
        invoice.setInvoiceDate(rs.getObject("InvoiceDate", java.time.LocalDateTime.class));
        invoice.setStatus(Invoice.InvoiceStatus.valueOf(rs.getString("Status")));
        invoice.setSubtotal(rs.getBigDecimal("Subtotal"));
        invoice.setDiscountAmount(rs.getBigDecimal("DiscountAmount"));
        invoice.setTotalAmount(rs.getBigDecimal("TotalAmount"));
        
        String paymentMethod = rs.getString("PaymentMethod");
        if (!rs.wasNull() && paymentMethod != null) {
            invoice.setPaymentMethod(Invoice.PaymentMethod.valueOf(paymentMethod));
        }
        
        invoice.setNote(rs.getString("Note"));
        
        return invoice;
    }
}
