package services;

import domain.AuditLog;
import domain.User;
import dto.AuditLogResponse;
import org.junit.jupiter.api.Test;
import repositories.AuditLogRepository;
import repositories.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class AuditServiceTest {
    @Test
    void write_truncatesDetailsAndSetsUtcTimestamp() {
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-26T00:00:00Z"), ZoneOffset.UTC);
        AuditService service = new AuditService(auditLogRepository, mock(UserRepository.class), clock);
        String details = "x".repeat(1001);

        service.write(7, 2, "ROLE_UPDATED", "Role", 4L, details);

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).write(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getDetails()).hasSize(1000);
        assertThat(auditCaptor.getValue().getCreatedAt()).isEqualTo(clock.instant());
    }

    @Test
    void write_auditRepositoryFailure_doesNotBreakBusinessCall() {
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        when(auditLogRepository.write(any(AuditLog.class))).thenThrow(new IllegalStateException("database unavailable"));
        AuditService service = new AuditService(
                auditLogRepository, mock(UserRepository.class), Clock.systemUTC());

        service.write(7, null, "ROLE_UPDATED", "Role", 4L, "role changed");
    }

    @Test
    void searchAuditLogs_clampsPaginationAndMapsUsername() {
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AuditLog auditLog = new AuditLog();
        auditLog.setAuditLogId(11L);
        auditLog.setUserId(7);
        auditLog.setActionCode("ROLE_UPDATED");
        auditLog.setEntityName("Role");
        when(auditLogRepository.search(7, "ROLE_UPDATED", "Role", null, null, 1, 100, "DESC"))
                .thenReturn(List.of(auditLog));
        when(auditLogRepository.count(7, "ROLE_UPDATED", "Role", null, null)).thenReturn(1L);
        User user = new User();
        user.setUsername("admin.demo");
        when(userRepository.getById(7)).thenReturn(Optional.of(user));
        AuditService service = new AuditService(auditLogRepository, userRepository, Clock.systemUTC());

        var result = service.searchAuditLogs(
                7, "ROLE_UPDATED", "Role", null, null, 0, 500, "invalid");

        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(100);
        assertThat(result.getItems()).singleElement()
                .satisfies((AuditLogResponse item) -> assertThat(item.getUsername()).isEqualTo("admin.demo"));
    }
}
