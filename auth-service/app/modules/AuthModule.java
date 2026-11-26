package modules;

import clients.BranchLookupClient;
import clients.FakeBranchLookupClient;
import clients.HttpBranchLookupClient;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.typesafe.config.Config;
import play.Environment;
import play.libs.ws.WSClient;
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
        // Clock - cho phép kiểm soát thời gian trong kiểm thử
        bind(Clock.class).toInstance(Clock.systemUTC());
        
        // Bảo mật
        bind(PasswordHasher.class).to(BCryptPasswordHasher.class);
        bind(TokenService.class).to(JwtTokenService.class);
        
        // Các repository
        bind(UserRepository.class).to(JdbcUserRepository.class);
        bind(RoleRepository.class).to(JdbcRoleRepository.class);
        bind(PermissionRepository.class).to(JdbcPermissionRepository.class);
        bind(RolePermissionRepository.class).to(JdbcRolePermissionRepository.class);
        bind(UserBranchRepository.class).to(JdbcUserBranchRepository.class);
        bind(RefreshTokenRepository.class).to(JdbcRefreshTokenRepository.class);
        bind(AuditLogRepository.class).to(JdbcAuditLogRepository.class);
        
        // Seeder - khởi tạo sớm khi ứng dụng khởi động
        bind(AdminDevSeeder.class).asEagerSingleton();
    }

    @Provides
    @javax.inject.Singleton
    BranchLookupClient provideBranchLookupClient(
            WSClient wsClient,
            Config config,
            Environment environment) {
        if (environment.isProd()) {
            return new HttpBranchLookupClient(wsClient, config);
        }
        return new FakeBranchLookupClient();
    }
}
