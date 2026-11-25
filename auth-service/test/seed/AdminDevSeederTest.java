package seed;

import clients.BranchLookupClient;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import domain.Role;
import domain.RoleCodes;
import domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import play.Environment;
import play.Mode;
import repositories.RoleRepository;
import repositories.UserBranchRepository;
import repositories.UserRepository;
import security.PasswordHasher;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho AdminDevSeeder
 */
public class AdminDevSeederTest {
    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private UserBranchRepository userBranchRepository;
    private BranchLookupClient branchLookupClient;
    private PasswordHasher passwordHasher;
    private Environment environment;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        userBranchRepository = mock(UserBranchRepository.class);
        branchLookupClient = mock(BranchLookupClient.class);
        passwordHasher = mock(PasswordHasher.class);
        environment = mock(Environment.class);

        // Mock branch lookup
        when(branchLookupClient.getAllActiveBranchIds())
            .thenReturn(CompletableFuture.completedFuture(Arrays.asList(1, 2, 3)));

        // Mock password hasher
        when(passwordHasher.hash(anyString())).thenReturn("$2a$11$hashedpassword");
    }

    @Test
    void seeder_shouldNotRunInProduction() {
        // Arrange
        Config config = ConfigFactory.parseString("seed.enableDevAdmin=false");
        when(environment.isProd()).thenReturn(true);
        
        // Act
        new AdminDevSeeder(userRepository, roleRepository, userBranchRepository,
                          branchLookupClient, passwordHasher, config, environment);
        
        // Assert - không có gọi repository nào
        verify(userRepository, never()).getByUsername(anyString());
        verify(userRepository, never()).create(any());
    }

    @Test
    void seeder_shouldRunInDevMode() {
        // Arrange
        Config config = ConfigFactory.parseString(
            "seed.enableDevAdmin=true\n" +
            "seed.admin.username=admin.demo\n" +
            "seed.admin.email=admin@example.local\n" +
            "seed.admin.password=Admin@12345"
        );
        when(environment.isProd()).thenReturn(false);
        
        Role adminRole = new Role(1, RoleCodes.ADMIN, "Admin", true);
        when(roleRepository.getByCode(RoleCodes.ADMIN)).thenReturn(Optional.of(adminRole));
        when(userRepository.getByUsername("admin.demo")).thenReturn(Optional.empty());
        when(userRepository.create(any(User.class))).thenReturn(1);
        
        // Act
        new AdminDevSeeder(userRepository, roleRepository, userBranchRepository,
                          branchLookupClient, passwordHasher, config, environment);
        
        // Assert
        verify(userRepository).create(any(User.class));
        verify(userBranchRepository).replaceBranches(eq(1), anyList());
    }

    @Test
    void seeder_shouldUpdateUserWithPlaceholderHash() {
        // Arrange
        Config config = ConfigFactory.parseString(
            "seed.enableDevAdmin=true\n" +
            "seed.admin.username=admin.demo\n" +
            "seed.admin.email=admin@example.local\n" +
            "seed.admin.password=Admin@12345"
        );
        when(environment.isProd()).thenReturn(false);
        
        Role adminRole = new Role(1, RoleCodes.ADMIN, "Admin", true);
        when(roleRepository.getByCode(RoleCodes.ADMIN)).thenReturn(Optional.of(adminRole));
        
        User existingUser = new User();
        existingUser.setUserId(1);
        existingUser.setUsername("admin.demo");
        existingUser.setPasswordHash("REPLACE_WITH_BCRYPT_HASH");
        existingUser.setLocked(false);
        existingUser.setActive(true);
        
        when(userRepository.getByUsername("admin.demo")).thenReturn(Optional.of(existingUser));
        
        // Act
        new AdminDevSeeder(userRepository, roleRepository, userBranchRepository,
                          branchLookupClient, passwordHasher, config, environment);
        
        // Assert
        verify(userRepository).updatePasswordHash(eq(1), anyString());
        verify(userBranchRepository).replaceBranches(eq(1), anyList());
    }

    @Test
    void seeder_shouldNotUpdateUserWithValidHash() {
        // Arrange
        Config config = ConfigFactory.parseString(
            "seed.enableDevAdmin=true\n" +
            "seed.admin.username=admin.demo\n" +
            "seed.admin.email=admin@example.local\n" +
            "seed.admin.password=Admin@12345"
        );
        when(environment.isProd()).thenReturn(false);
        
        Role adminRole = new Role(1, RoleCodes.ADMIN, "Admin", true);
        when(roleRepository.getByCode(RoleCodes.ADMIN)).thenReturn(Optional.of(adminRole));
        
        User existingUser = new User();
        existingUser.setUserId(1);
        existingUser.setUsername("admin.demo");
        existingUser.setPasswordHash("$2a$11$validhashfromproduction");
        existingUser.setLocked(false);
        existingUser.setActive(true);
        
        when(userRepository.getByUsername("admin.demo")).thenReturn(Optional.of(existingUser));
        
        // Act
        new AdminDevSeeder(userRepository, roleRepository, userBranchRepository,
                          branchLookupClient, passwordHasher, config, environment);
        
        // Assert - không update password
        verify(userRepository, never()).updatePasswordHash(anyInt(), anyString());
    }

    @Test
    void seeder_shouldCreateUserWithAllBranchesForAdmin() {
        // Arrange
        Config config = ConfigFactory.parseString(
            "seed.enableDevAdmin=true\n" +
            "seed.admin.username=admin.demo\n" +
            "seed.admin.email=admin@example.local\n" +
            "seed.admin.password=Admin@12345"
        );
        when(environment.isProd()).thenReturn(false);
        
        Role adminRole = new Role(1, RoleCodes.ADMIN, "Admin", true);
        when(roleRepository.getByCode(RoleCodes.ADMIN)).thenReturn(Optional.of(adminRole));
        when(userRepository.getByUsername("admin.demo")).thenReturn(Optional.empty());
        when(userRepository.create(any(User.class))).thenReturn(1);
        
        // Act
        new AdminDevSeeder(userRepository, roleRepository, userBranchRepository,
                          branchLookupClient, passwordHasher, config, environment);
        
        // Assert - admin được gán tất cả branches
        ArgumentCaptor<List<Integer>> branchCaptor = ArgumentCaptor.forClass(List.class);
        verify(userBranchRepository).replaceBranches(eq(1), branchCaptor.capture());
        
        List<Integer> assignedBranches = branchCaptor.getValue();
        assertThat(assignedBranches).containsExactly(1, 2, 3);
    }
}
