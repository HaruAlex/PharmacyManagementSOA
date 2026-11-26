package services;

import domain.Permission;
import domain.Role;
import domain.AuditActions;
import dto.CreateRoleRequest;
import dto.CreatePermissionRequest;
import dto.UpdateRolePermissionsRequest;
import dto.UpdateRoleRequest;
import dto.UpdatePermissionRequest;
import dto.UpdateRoleRequest;
import errors.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import repositories.PermissionRepository;
import repositories.RefreshTokenRepository;
import repositories.RoleRepository;
import repositories.UserRepository;
import security.UserStatusCache;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RbacServiceTest {
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserStatusCache userStatusCache;
    @Mock private AuditService auditService;

    private RbacService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new RbacService(
                roleRepository,
                permissionRepository,
                userRepository,
                refreshTokenRepository,
                userStatusCache,
                auditService
        );
    }

    @Test
    void createRole_duplicateCode_returnsConflict() {
        CreateRoleRequest request = new CreateRoleRequest();
        request.setCode(" wh ");
        request.setName("Warehouse");
        when(roleRepository.existsByCode("WH")).thenReturn(true);

        assertThatThrownBy(() -> service.createRole(request, 1, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Role code already exists")
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(409));
        verify(roleRepository, never()).create(any(Role.class));
    }

    @Test
    void createRole_validRequest_normalizesCodeAndAudits() {
        CreateRoleRequest request = new CreateRoleRequest();
        request.setCode("  wh2 ");
        request.setName(" Warehouse Two ");
        when(roleRepository.existsByCode("WH2")).thenReturn(false);
        when(roleRepository.create(any(Role.class))).thenReturn(5);

        var response = service.createRole(request, 1, "127.0.0.1");

        assertThat(response.getRoleId()).isEqualTo(5);
        assertThat(response.getRoleCode()).isEqualTo("WH2");
        assertThat(response.getRoleName()).isEqualTo("Warehouse Two");
        verify(auditService).write(eq(1), eq(null), eq(AuditActions.ROLE_CREATED), eq("Role"), eq(5L), any());
    }

    @Test
    void listRolesAndGetRole_mapRepositoryResults() {
        Role role = new Role(2, "WH", "Warehouse", true);
        when(roleRepository.getAll()).thenReturn(List.of(role));
        when(roleRepository.getById(2)).thenReturn(Optional.of(role));

        assertThat(service.listRoles()).singleElement()
                .satisfies(item -> assertThat(item.getRoleCode()).isEqualTo("WH"));
        assertThat(service.getRole(2).getRoleName()).isEqualTo("Warehouse");
    }

    @Test
    void updateRole_deactivateRoleWithActiveUsers_returnsConflict() {
        Role role = new Role(2, "WH", "Warehouse", true);
        when(roleRepository.getById(2)).thenReturn(Optional.of(role));
        when(roleRepository.countActiveUsersByRole(2)).thenReturn(1L);
        UpdateRoleRequest request = new UpdateRoleRequest();
        request.setName("Warehouse");
        request.setIsActive(false);

        assertThatThrownBy(() -> service.updateRole(2, request, 1, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(409));
        verify(roleRepository, never()).update(any(Role.class));
    }

    @Test
    void updateRole_deactivateUnusedRole_updatesAndEvictsRoleUsers() {
        Role role = new Role(2, "WH", "Warehouse", true);
        when(roleRepository.getById(2)).thenReturn(Optional.of(role));
        when(roleRepository.countActiveUsersByRole(2)).thenReturn(0L);
        when(userRepository.getUserIdsByRoleId(2)).thenReturn(List.of(12));
        UpdateRoleRequest request = new UpdateRoleRequest();
        request.setName("Warehouse Staff");
        request.setIsActive(false);

        var response = service.updateRole(2, request, 1, "127.0.0.1");

        assertThat(response.getRoleName()).isEqualTo("Warehouse Staff");
        assertThat(response.getIsActive()).isFalse();
        verify(roleRepository).update(role);
        verify(userStatusCache).evictMultiple(List.of(12));
        verify(auditService).write(eq(1), eq(null), eq(AuditActions.ROLE_DEACTIVATED),
                eq("Role"), eq(2L), any());
    }

    @Test
    void createPermission_validRequest_normalizesCodeAndAudits() {
        CreatePermissionRequest request = new CreatePermissionRequest();
        request.setCode(" stock_view ");
        request.setName(" View stock ");
        when(permissionRepository.existsByCode("STOCK_VIEW")).thenReturn(false);
        when(permissionRepository.create(any(Permission.class))).thenReturn(9);

        var response = service.createPermission(request, 1, "127.0.0.1");

        assertThat(response.getPermissionId()).isEqualTo(9);
        assertThat(response.getPermissionCode()).isEqualTo("STOCK_VIEW");
        assertThat(response.getPermissionName()).isEqualTo("View stock");
        verify(auditService).write(eq(1), eq(null), eq(AuditActions.PERMISSION_CREATED),
                eq("Permission"), eq(9L), any());
    }

    @Test
    void listGetAndUpdatePermissions_returnMappedPermission() {
        Permission permission = new Permission(9, "STOCK_VIEW", "View stock");
        when(permissionRepository.getAll()).thenReturn(List.of(permission));
        when(permissionRepository.getById(9)).thenReturn(Optional.of(permission));
        UpdatePermissionRequest request = new UpdatePermissionRequest();
        request.setName(" View inventory ");

        assertThat(service.listPermissions()).hasSize(1);
        assertThat(service.getPermission(9).getPermissionCode()).isEqualTo("STOCK_VIEW");
        assertThat(service.updatePermission(9, request, 1, "127.0.0.1").getPermissionName())
                .isEqualTo("View inventory");
        verify(permissionRepository).update(permission);
    }

    @Test
    void getRolePermissions_returnsAssignedPermissions() {
        when(roleRepository.getById(2)).thenReturn(Optional.of(new Role(2, "WH", "Warehouse", true)));
        when(permissionRepository.getByRoleId(2))
                .thenReturn(List.of(new Permission(9, "STOCK_VIEW", "View stock")));

        assertThat(service.getRolePermissions(2)).singleElement()
                .satisfies(item -> assertThat(item.getPermissionCode()).isEqualTo("STOCK_VIEW"));
    }

    @Test
    void updateRolePermissions_removingUserManageFromAdmin_isForbidden() {
        Role adminRole = new Role(1, "AD", "Administrator", true);
        when(roleRepository.getById(1)).thenReturn(Optional.of(adminRole));
        UpdateRolePermissionsRequest request = new UpdateRolePermissionsRequest();
        request.setPermissionIds(List.of());

        assertThatThrownBy(() -> service.updateRolePermissions(1, request, 1, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(403));
        verify(permissionRepository, never()).replaceRolePermissions(eq(1), anyList());
    }

    @Test
    void updateRolePermissions_success_replacesAndRevokesRoleUsers() {
        Role warehouseRole = new Role(2, "WH", "Warehouse", true);
        Permission permission = new Permission(8, "INVENTORY_MANAGE", "Manage inventory");
        when(roleRepository.getById(2)).thenReturn(Optional.of(warehouseRole));
        when(permissionRepository.getById(8)).thenReturn(Optional.of(permission));
        when(userRepository.getUserIdsByRoleId(2)).thenReturn(List.of(12));
        when(permissionRepository.getByRoleId(2)).thenReturn(List.of(permission));

        UpdateRolePermissionsRequest request = new UpdateRolePermissionsRequest();
        request.setPermissionIds(List.of(8));

        List<dto.PermissionResponse> response =
                service.updateRolePermissions(2, request, 1, "127.0.0.1");
        assertThat(response).hasSize(1);
        assertThat(response.get(0).getPermissionCode()).isEqualTo("INVENTORY_MANAGE");

        verify(permissionRepository).replaceRolePermissions(2, List.of(8));
        verify(userStatusCache).evictMultiple(List.of(12));
        verify(refreshTokenRepository).revokeAllByUserId(12);
        verify(auditService).write(
                eq(1), eq(null), eq(AuditActions.ROLE_PERMISSIONS_CHANGED), eq("Role"),
                eq(2L), any()
        );
    }
}
