package modules;

import clients.BranchLookupClient;
import clients.FakeBranchLookupClient;
import com.google.inject.AbstractModule;
import repositories.*;
import repositories.jdbc.*;
import security.BCryptPasswordHasher;
import security.JwtTokenService;
import security.PasswordHasher;
import security.TokenService;
import seed.AdminDevSeeder;

import java.time.Clock;

/**
 * Guice module cho Auth Service
 * Cấu hình dependency injection bindings
 */
public class AuthModule extends AbstractModule {
    @Override
    protected void configure() {
        // Clock - cho phép mock trong test
        bind(Clock.class).toInstance(Clock.systemUTC());
        
        // Security
        bind(PasswordHasher.class).to(BCryptPasswordHasher.class);
        bind(TokenService.class).to(JwtTokenService.class);
        
        // Repositories
        bind(UserRepository.class).to(JdbcUserRepository.class);
        bind(RoleRepository.class).to(JdbcRoleRepository.class);
        bind(PermissionRepository.class).to(JdbcPermissionRepository.class);
        bind(RolePermissionRepository.class).to(JdbcRolePermissionRepository.class);
        bind(UserBranchRepository.class).to(JdbcUserBranchRepository.class);
        bind(RefreshTokenRepository.class).to(JdbcRefreshTokenRepository.class);
        bind(AuditLogRepository.class).to(JdbcAuditLogRepository.class);
        
        // Clients - sử dụng Fake client cho dev/test
        // TODO: Phase sau sẽ thêm HTTP client thật và chọn dựa vào config
        bind(BranchLookupClient.class).to(FakeBranchLookupClient.class);
        
        // Seeder - eager singleton để chạy ngay khi app start
        bind(AdminDevSeeder.class).asEagerSingleton();
    }
}
