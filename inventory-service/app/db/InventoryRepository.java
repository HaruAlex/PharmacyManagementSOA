package db;

import play.Logger;

import java.sql.*;

/**
 * ============================================================================
 * INVENTORY REPOSITORY - FEFO LOCK (First-In-First-Out)
 * ============================================================================
 * 
 * Xử lý inventory operations với UPDLOCK + ROWLOCK
 * để tránh race condition khi 2 instances update cùng lúc.
 * 
 * FEFO = First-In-First-Out locking (người gọi trước được lock trước)
 * 
 * Đợt 4: FEFO Lock + An toàn dữ liệu (Safety)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class InventoryRepository {
    
    /**
     * Trừ tồn hàng (Deduct inventory) - với UPDLOCK
     * 
     * SQL pattern:
     * BEGIN TRANSACTION
     *   SELECT ... WITH (UPDLOCK, ROWLOCK) FROM Inventory WHERE ...
     *   UPDATE Inventory SET quantity = quantity - deductAmount WHERE id = ...
     * COMMIT
     * 
     * @param productId ID sản phẩm
     * @param deductAmount Số lượng trừ
     * @return true nếu thành công, false nếu không đủ tồn
     */
    public static boolean deductInventory(String productId, int deductAmount) {
        Logger.info("[InventoryRepo] Deducting " + deductAmount + " units for product: " + productId);
        
        String selectQuery = "SELECT quantity, version FROM [Inventory] " +
                             "WITH (UPDLOCK, ROWLOCK) " +
                             "WHERE product_id = ? AND status = 'active'";
        
        String updateQuery = "UPDATE [Inventory] " +
                            "SET quantity = quantity - ?, version = version + 1 " +
                            "WHERE product_id = ? AND version = ? AND status = 'active'";
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            // BEGIN TRANSACTION
            stmt.execute("BEGIN TRANSACTION");
            
            try (PreparedStatement selectStmt = conn.prepareStatement(selectQuery)) {
                selectStmt.setString(1, productId);
                
                try (ResultSet rs = selectStmt.executeQuery()) {
                    if (!rs.next()) {
                        Logger.warn("[InventoryRepo] ✗ Product not found: " + productId);
                        stmt.execute("ROLLBACK");
                        return false;
                    }
                    
                    int currentQuantity = rs.getInt("quantity");
                    int currentVersion = rs.getInt("version");
                    
                    // Check nếu đủ tồn
                    if (currentQuantity < deductAmount) {
                        Logger.warn("[InventoryRepo] ✗ Not enough quantity. Current: " + currentQuantity +
                                   ", Deduct: " + deductAmount);
                        stmt.execute("ROLLBACK");
                        return false;
                    }
                    
                    // UPDATE với version check (optimistic lock)
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {
                        updateStmt.setInt(1, deductAmount);
                        updateStmt.setString(2, productId);
                        updateStmt.setInt(3, currentVersion);
                        
                        int rowsAffected = updateStmt.executeUpdate();
                        
                        if (rowsAffected == 0) {
                            Logger.warn("[InventoryRepo] ✗ Version mismatch (concurrent update detected)");
                            stmt.execute("ROLLBACK");
                            return false;
                        }
                        
                        // COMMIT
                        stmt.execute("COMMIT");
                        Logger.info("[InventoryRepo] ✓ Successfully deducted " + deductAmount + 
                                   " units. New quantity: " + (currentQuantity - deductAmount) +
                                   ", Version: " + (currentVersion + 1));
                        return true;
                    }
                }
            } catch (Exception e) {
                stmt.execute("ROLLBACK");
                throw e;
            }
            
        } catch (Exception e) {
            Logger.error("[InventoryRepo] Error deducting inventory: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Thêm tồn hàng (Add inventory)
     * 
     * @param productId ID sản phẩm
     * @param addAmount Số lượng thêm
     * @return true nếu thành công
     */
    public static boolean addInventory(String productId, int addAmount) {
        Logger.info("[InventoryRepo] Adding " + addAmount + " units for product: " + productId);
        
        String updateQuery = "UPDATE [Inventory] " +
                            "SET quantity = quantity + ?, version = version + 1 " +
                            "WHERE product_id = ? AND status = 'active'";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
            
            stmt.setInt(1, addAmount);
            stmt.setString(2, productId);
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                Logger.info("[InventoryRepo] ✓ Successfully added " + addAmount + " units");
                return true;
            } else {
                Logger.warn("[InventoryRepo] ✗ Product not found: " + productId);
                return false;
            }
            
        } catch (Exception e) {
            Logger.error("[InventoryRepo] Error adding inventory: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Lấy inventory info
     */
    public static InventoryInfo getInventory(String productId) {
        Logger.info("[InventoryRepo] Getting inventory for product: " + productId);
        
        String query = "SELECT product_id, quantity, version, status FROM [Inventory] " +
                      "WHERE product_id = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, productId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new InventoryInfo(
                        rs.getString("product_id"),
                        rs.getInt("quantity"),
                        rs.getInt("version"),
                        rs.getString("status")
                    );
                }
            }
            
        } catch (Exception e) {
            Logger.error("[InventoryRepo] Error getting inventory: " + e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * Get SQL Server connection
     */
    private static Connection getConnection() throws SQLException {
        // TODO: Replace with actual datasource from connection pool
        String url = "jdbc:sqlserver://localhost:1433;databaseName=PharmacyManagementSOA;encrypt=false";
        String user = "sa";
        String password = "123456";
        
        return DriverManager.getConnection(url, user, password);
    }
    
    /**
     * Inventory info model
     */
    public static class InventoryInfo {
        public String productId;
        public int quantity;
        public int version;  // For optimistic locking
        public String status;
        
        public InventoryInfo(String productId, int quantity, int version, String status) {
            this.productId = productId;
            this.quantity = quantity;
            this.version = version;
            this.status = status;
        }
    }
}
