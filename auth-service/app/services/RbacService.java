package services;

import domain.*;
import dto.*;
import errors.ApiException;
import repositories.*;
import security.UserStatusCache;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service xử lý RBAC (Role-Based Access Control)
 */
@Singleton
public class RbacService {
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserStatusCache userStatusCache;
    private final AuditService auditService;

    @Inject
    public RbacService(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            UserStatusCache userStatusCache,
            AuditService auditService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userStatusCache = userStatusCache;
        this.auditService = auditService;
    }

    // ========================================================================
    // ROLE MANAGEMENT
    // ========================================================================

    /**
     * Tạo role mới
     */
    public RoleResponse createRole(CreateRoleRequest request, Integer adminUserId, String ipAddress) {
        // Kiểm tra dữ liệu đầu vào
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "Role code is required");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "Role name is required");
        }

        // Kiểm tra RoleCode chưa được sử dụng
        String roleCode = request.getCode().trim().toUpperCase();
        if (roleRepository.existsByCode(roleCode)) {
            throw ApiException.conflict(ErrorCodes.ROLE_CODE_EXISTS, "Role code already exists");
        }

        // Tạo vai trò
        Role role = new Role();
        role.setRoleCode(roleCode);
        role.setRoleName(request.getName().trim());
        role.setActive(request.getIsActive() != null ? request.getIsActive() : true);

        Integer roleId = roleRepository.create(role);
        role.setRoleId(roleId);

        // Audit
        auditService.write(adminUserId, null, AuditActions.ROLE_CREATED, "Role", roleId.longValue(),
                String.format("Role '%s' created, IP: %s", roleCode, ipAddress));

        return buildRoleResponse(role);
    }

    /**
     * Lấy danh sách roles
     */
    public List<RoleResponse> listRoles() {
        List<Role> roles = roleRepository.getAll();
        return roles.stream()
                .map(this::buildRoleResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lấy chi tiết role
     */
    public RoleResponse getRole(Integer roleId) {
        Role role = roleRepository.getById(roleId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.ROLE_NOT_FOUND, "Role not found"));
        return buildRoleResponse(role);
    }

    /**
     * Cập nhật role (chỉ name và isActive, không đổi code)
     */
    public RoleResponse updateRole(Integer roleId, UpdateRoleRequest request, Integer adminUserId, String ipAddress) {
        Role role = roleRepository.getById(roleId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.ROLE_NOT_FOUND, "Role not found"));

        // Kiểm tra dữ liệu đầu vào
        if (request.getName() == null || request.getName().isBlank()) {
            throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "Role name is required");
        }

        boolean wasActive = role.isActive();
        boolean willBeActive = request.getIsActive() != null ? request.getIsActive() : wasActive;

        // Kiểm tra nếu vô hiệu hóa role đang có user active
        if (wasActive && !willBeActive) {
            long activeUserCount = roleRepository.countActiveUsersByRole(roleId);
            if (activeUserCount > 0) {
                throw ApiException.conflict(ErrorCodes.ROLE_IN_USE,
                        String.format("Cannot deactivate role with %d active users", activeUserCount));
            }
        }

        // Cập nhật vai trò
        role.setRoleName(request.getName().trim());
        role.setActive(willBeActive);
        roleRepository.update(role);

        // Audit
        String action = !wasActive && willBeActive ? AuditActions.ROLE_ACTIVATED :
                       wasActive && !willBeActive ? AuditActions.ROLE_DEACTIVATED :
                       AuditActions.ROLE_UPDATED;

        auditService.write(adminUserId, null, action, "Role", roleId.longValue(),
                String.format("Role '%s' updated, IP: %s", role.getRoleCode(), ipAddress));

        // Nếu đổi trạng thái role, evict cache của users thuộc role đó
        if (wasActive != willBeActive) {
            evictCacheForRole(roleId);
        }

        return buildRoleResponse(role);
    }

    // ========================================================================
    // PERMISSION MANAGEMENT
    // ========================================================================

    /**
     * Tạo permission mới
     */
    public PermissionResponse createPermission(CreatePermissionRequest request, Integer adminUserId, String ipAddress) {
        // Kiểm tra dữ liệu đầu vào
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "Permission code is required");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "Permission name is required");
        }

        // Kiểm tra PermissionCode chưa được sử dụng
        String permissionCode = request.getCode().trim().toUpperCase();
        if (permissionRepository.existsByCode(permissionCode)) {
            throw ApiException.conflict(ErrorCodes.PERMISSION_CODE_EXISTS, "Permission code already exists");
        }

        // Tạo quyền
        Permission permission = new Permission();
        permission.setPermissionCode(permissionCode);
        permission.setPermissionName(request.getName().trim());

        Integer permissionId = permissionRepository.create(permission);
        permission.setPermissionId(permissionId);

        // Audit
        auditService.write(adminUserId, null, AuditActions.PERMISSION_CREATED, "Permission", permissionId.longValue(),
                String.format("Permission '%s' created, IP: %s", permissionCode, ipAddress));

        return buildPermissionResponse(permission);
    }

    /**
     * Lấy danh sách permissions
     */
    public List<PermissionResponse> listPermissions() {
        List<Permission> permissions = permissionRepository.getAll();
        return permissions.stream()
                .map(this::buildPermissionResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lấy chi tiết permission
     */
    public PermissionResponse getPermission(Integer permissionId) {
        Permission permission = permissionRepository.getById(permissionId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.PERMISSION_NOT_FOUND, "Permission not found"));
        return buildPermissionResponse(permission);
    }

    /**
     * Cập nhật permission (chỉ name, không đổi code)
     */
    public PermissionResponse updatePermission(Integer permissionId, UpdatePermissionRequest request,
                                               Integer adminUserId, String ipAddress) {
        Permission permission = permissionRepository.getById(permissionId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.PERMISSION_NOT_FOUND, "Permission not found"));

        // Kiểm tra dữ liệu đầu vào
        if (request.getName() == null || request.getName().isBlank()) {
            throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "Permission name is required");
        }

        // Cập nhật quyền
        permission.setPermissionName(request.getName().trim());
        permissionRepository.update(permission);

        // Audit
        auditService.write(adminUserId, null, AuditActions.PERMISSION_UPDATED, "Permission", permissionId.longValue(),
                String.format("Permission '%s' updated, IP: %s", permission.getPermissionCode(), ipAddress));

        return buildPermissionResponse(permission);
    }

    // ========================================================================
    // ROLE-PERMISSION MAPPING
    // ========================================================================

    /**
     * Lấy permissions của role
     */
    public List<PermissionResponse> getRolePermissions(Integer roleId) {
        // Kiểm tra vai trò tồn tại
        roleRepository.getById(roleId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.ROLE_NOT_FOUND, "Role not found"));

        List<Permission> permissions = permissionRepository.getByRoleId(roleId);
        return permissions.stream()
                .map(this::buildPermissionResponse)
                .collect(Collectors.toList());
    }

    /**
     * Cập nhật toàn bộ quyền của vai trò trong một transaction
     */
    public List<PermissionResponse> updateRolePermissions(Integer roleId, UpdateRolePermissionsRequest request,
                                                          Integer adminUserId, String ipAddress) {
        // Kiểm tra vai trò tồn tại
        Role role = roleRepository.getById(roleId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.ROLE_NOT_FOUND, "Role not found"));

        // Kiểm tra dữ liệu đầu vào
        if (request.getPermissionIds() == null) {
            throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "Permission IDs are required");
        }

        List<Integer> permissionIds = request.getPermissionIds();

        // Kiểm tra tất cả quyền đều tồn tại
        for (Integer permissionId : permissionIds) {
            if (!permissionRepository.getById(permissionId).isPresent()) {
                throw ApiException.badRequest(ErrorCodes.PERMISSION_NOT_FOUND,
                        String.format("Permission ID %d not found", permissionId));
            }
        }

        // Chặn gỡ USER_MANAGE khỏi role AD
        if ("AD".equals(role.getRoleCode())) {
            List<Permission> requestedPermissions = permissionIds.stream()
                    .map(id -> permissionRepository.getById(id).orElse(null))
                    .filter(p -> p != null)
                    .collect(Collectors.toList());

            boolean hasUserManage = requestedPermissions.stream()
                    .anyMatch(p -> "USER_MANAGE".equals(p.getPermissionCode()));

            if (!hasUserManage) {
                throw ApiException.forbidden(ErrorCodes.PERMISSION_CANNOT_REMOVE_FROM_ADMIN,
                        "Cannot remove USER_MANAGE permission from Admin role");
            }
        }

        permissionRepository.replaceRolePermissions(roleId, permissionIds);

        // Xóa cache và thu hồi token của người dùng thuộc vai trò này
        evictCacheForRole(roleId);
        revokeTokensForRole(roleId);

        // Audit
        auditService.write(adminUserId, null, AuditActions.ROLE_PERMISSIONS_CHANGED, "Role", roleId.longValue(),
                String.format("Permissions of role '%s' changed to %s, IP: %s",
                        role.getRoleCode(), permissionIds, ipAddress));

        // Trả về danh sách quyền sau cập nhật
        return getRolePermissions(roleId);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Xóa cache của tất cả người dùng thuộc vai trò
     */
    private void evictCacheForRole(Integer roleId) {
        List<Integer> userIds = userRepository.getUserIdsByRoleId(roleId);
        userStatusCache.evictMultiple(userIds);
    }

    /**
     * Thu hồi refresh token của tất cả người dùng thuộc vai trò
     */
    private void revokeTokensForRole(Integer roleId) {
        List<Integer> userIds = userRepository.getUserIdsByRoleId(roleId);
        for (Integer userId : userIds) {
            refreshTokenRepository.revokeAllByUserId(userId);
        }
    }

    private RoleResponse buildRoleResponse(Role role) {
        RoleResponse response = new RoleResponse();
        response.setRoleId(role.getRoleId());
        response.setRoleCode(role.getRoleCode());
        response.setRoleName(role.getRoleName());
        response.setIsActive(role.isActive());
        // Role không có CreatedAt/UpdatedAt trong entity
        response.setCreatedAt(null);
        response.setUpdatedAt(null);
        return response;
    }

    private PermissionResponse buildPermissionResponse(Permission permission) {
        PermissionResponse response = new PermissionResponse();
        response.setPermissionId(permission.getPermissionId());
        response.setPermissionCode(permission.getPermissionCode());
        response.setPermissionName(permission.getPermissionName());
        // Permission không có CreatedAt trong entity
        response.setCreatedAt(null);
        return response;
    }
}
