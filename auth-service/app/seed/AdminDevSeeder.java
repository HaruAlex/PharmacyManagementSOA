package seed;

import clients.BranchLookupClient;
import com.typesafe.config.Config;
import domain.Role;
import domain.RoleCodes;
import domain.User;
import play.Environment;
import play.Mode;
import repositories.RoleRepository;
import repositories.UserBranchRepository;
import repositories.UserRepository;
import security.PasswordHasher;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Seeder tạo tài khoản admin/warehouse/pharmacist cho môi trường dev/test
 * Chỉ chạy khi Play mode là Dev/Test hoặc seed.enableDevAdmin=true
 * Production KHÔNG BAO GIỜ chạy seeder này
 */
@Singleton
public class AdminDevSeeder {
    private static final String PLACEHOLDER_HASH = "REPLACE_WITH_BCRYPT_HASH";
    
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserBranchRepository userBranchRepository;
    private final BranchLookupClient branchLookupClient;
    private final PasswordHasher passwordHasher;
    private final Config config;
    private final Environment environment;

    @Inject
    public AdminDevSeeder(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserBranchRepository userBranchRepository,
            BranchLookupClient branchLookupClient,
            PasswordHasher passwordHasher,
            Config config,
            Environment environment) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userBranchRepository = userBranchRepository;
        this.branchLookupClient = branchLookupClient;
        this.passwordHasher = passwordHasher;
        this.config = config;
        this.environment = environment;
        
        // Chạy seed ngay khi khởi tạo
        seed();
    }

    /**
     * Chạy seed nếu điều kiện thỏa mãn
     */
    private void seed() {
        // Chỉ chạy khi NOT production hoặc có cấu hình enableDevAdmin=true
        boolean shouldSeed = !environment.isProd() || config.getBoolean("seed.enableDevAdmin");
        
        if (!shouldSeed) {
            System.out.println("[AdminDevSeeder] Skipping seeder in Production mode");
            return;
        }
        
        System.out.println("[AdminDevSeeder] Starting dev account seeding...");
        
        try {
            // Lấy tất cả branch IDs đang hoạt động
            List<Integer> allBranchIds = branchLookupClient.getAllActiveBranchIds()
                    .toCompletableFuture()
                    .join();
            
            // Seed admin
            seedUser(
                config.getString("seed.admin.username"),
                config.getString("seed.admin.email"),
                config.getString("seed.admin.password"),
                "Quản trị hệ thống",
                RoleCodes.ADMIN,
                allBranchIds  // Admin có quyền tất cả branches
            );
            
            // Seed warehouse (nếu có cấu hình)
            if (config.hasPath("seed.warehouse.username")) {
                // Warehouse chỉ có CN000 và CN001
                List<Integer> warehouseBranches = allBranchIds.stream()
                        .limit(2)
                        .toList();
                seedUser(
                    config.getString("seed.warehouse.username"),
                    config.getString("seed.warehouse.email"),
                    config.getString("seed.warehouse.password"),
                    "Nhân viên kho",
                    RoleCodes.WAREHOUSE,
                    warehouseBranches
                );
            }
            
            // Seed pharmacist (nếu có cấu hình)
            if (config.hasPath("seed.pharmacist.username")) {
                // Pharmacist chỉ có CN001
                List<Integer> pharmacistBranches = allBranchIds.stream()
                        .skip(1)
                        .limit(1)
                        .toList();
                seedUser(
                    config.getString("seed.pharmacist.username"),
                    config.getString("seed.pharmacist.email"),
                    config.getString("seed.pharmacist.password"),
                    "Dược sĩ bán hàng",
                    RoleCodes.SALES_PHARMACIST,
                    pharmacistBranches
                );
            }
            
            System.out.println("[AdminDevSeeder] Dev account seeding completed successfully");
            
        } catch (Exception e) {
            System.err.println("[AdminDevSeeder] Error during seeding: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Seed một user (idempotent)
     */
    private void seedUser(String username, String email, String password, 
                         String fullName, String roleCode, List<Integer> branchIds) {
        try {
            // Lấy role
            Optional<Role> roleOpt = roleRepository.getByCode(roleCode);
            if (roleOpt.isEmpty()) {
                System.err.println("[AdminDevSeeder] Role not found: " + roleCode);
                return;
            }
            Role role = roleOpt.get();
            
            // Kiểm tra user đã tồn tại chưa
            Optional<User> existingUser = userRepository.getByUsername(username);
            
            if (existingUser.isPresent()) {
                User user = existingUser.get();
                
                // Chỉ cập nhật nếu password hash là placeholder hoặc rỗng
                if (PLACEHOLDER_HASH.equals(user.getPasswordHash()) || 
                    user.getPasswordHash() == null || 
                    user.getPasswordHash().trim().isEmpty()) {
                    
                    String hashedPassword = passwordHasher.hash(password);
                    userRepository.updatePasswordHash(user.getUserId(), hashedPassword);
                    
                    // Đảm bảo user được unlock và active
                    if (user.isLocked() || !user.isActive() || user.getFailedLoginCount() > 0) {
                        userRepository.setLocked(user.getUserId(), false);
                        userRepository.setActive(user.getUserId(), true);
                        userRepository.resetFailedLogin(user.getUserId());
                    }
                    
                    // Cập nhật branches
                    userBranchRepository.replaceBranches(user.getUserId(), branchIds);
                    
                    System.out.println("[AdminDevSeeder] Updated existing user: " + username);
                } else {
                    System.out.println("[AdminDevSeeder] User already exists with valid password: " + username);
                }
                
            } else {
                // Tạo user mới
                User newUser = new User();
                newUser.setRoleId(role.getRoleId());
                newUser.setUsername(username);
                newUser.setPasswordHash(passwordHasher.hash(password));
                newUser.setFullName(fullName);
                newUser.setEmail(email);
                newUser.setPhone(null);
                newUser.setLocked(false);
                newUser.setFailedLoginCount(0);
                newUser.setActive(true);
                
                Integer userId = userRepository.create(newUser);
                
                // Gán branches
                userBranchRepository.replaceBranches(userId, branchIds);
                
                System.out.println("[AdminDevSeeder] Created new user: " + username + " (ID: " + userId + ")");
            }
            
        } catch (Exception e) {
            System.err.println("[AdminDevSeeder] Error seeding user " + username + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
