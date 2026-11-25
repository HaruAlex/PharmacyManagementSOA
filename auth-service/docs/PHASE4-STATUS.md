# Phase 4 - User Management (In Progress)

## Status: 30% Complete

### ✅ Completed
1. **DTOs Created (6 files)**
   - `CreateUserRequest.java` - Validation cho tạo user mới
   - `UpdateUserRequest.java` - Cập nhật user (không có username field)
   - `UpdateRoleBranchesRequest.java` - Gán role + branches
   - `ResetPasswordRequest.java` - Admin reset password
   - `UserResponse.java` - Response DTO với đầy đủ thông tin
   - `PagedResult.java` - Generic paging response

2. **UserService.java Created**
   - `createUser()` - Validate password policy, check username/email unique, handle race condition
   - `listUsers()` - Paging + filtering (name, email, roleId, status), sort whitelist
   - `getUser()` - Chi tiết user
   - `updateUser()` - Cập nhật (email unique check, không đổi username)
   - `updateRoleAndBranches()` - Gán role + branches, revoke tokens, clear cache
   - `lockUser()` - Khóa user, check không tự khóa, không khóa admin cuối cùng
   - `unlockUser()` - Mở khóa, reset failed login count
   - `deleteUser()` - Xóa mềm (IsActive=0), check không tự xóa, không xóa admin cuối
   - `activateUser()` - Kích hoạt lại user
   - `resetPassword()` - Admin reset password, revoke tokens
   - `validatePassword()` - Kiểm tra password policy (minLength, uppercase, lowercase, digit, special char)

3. **UserRepository Interface Updated**
   - Added `search()` method with paging and filtering
   - Added `count()` method
   - Changed `countActiveAdmins()` return type to `long`

### ❌ Pending (70%)
1. **JdbcUserRepository Implementation**
   - Current file has old `search(String status)` signature
   - Need to update to `search(Boolean isActive, Boolean isLocked)`
   - Need to rename `countSearch()` to `count()`
   - Need to fix `countActiveAdmins()` return type (int -> long)

2. **UserController** (Not Created)
   - POST /api/users
   - GET /api/users (paging, filtering)
   - GET /api/users/:id
   - PUT /api/users/:id
   - PUT /api/users/:id/role-branches
   - POST /api/users/:id/lock
   - POST /api/users/:id/unlock
   - DELETE /api/users/:id (soft delete)
   - POST /api/users/:id/activate
   - POST /api/users/:id/reset-password

3. **Error Codes** (Need to Add to ErrorCodes.java)
   ```java
   public static final String USER_USERNAME_EXISTS = "USER_USERNAME_EXISTS";
   public static final String USER_EMAIL_EXISTS = "USER_EMAIL_EXISTS";
   public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
   public static final String ROLE_NOT_FOUND = "ROLE_NOT_FOUND";
   public static final String ROLE_INACTIVE = "ROLE_INACTIVE";
   public static final String PASSWORD_WEAK = "PASSWORD_WEAK";
   public static final String CANNOT_LOCK_SELF = "CANNOT_LOCK_SELF";
   public static final String CANNOT_LOCK_LAST_ADMIN = "CANNOT_LOCK_LAST_ADMIN";
   public static final String CANNOT_DELETE_SELF = "CANNOT_DELETE_SELF";
   public static final String CANNOT_DELETE_LAST_ADMIN = "CANNOT_DELETE_LAST_ADMIN";
   ```

4. **Audit Actions** (Need to Add to AuditActions.java)
   ```java
   public static final String USER_CREATED = "USER_CREATED";
   public static final String USER_UPDATED = "USER_UPDATED";
   public static final String USER_ROLE_CHANGED = "USER_ROLE_CHANGED";
   public static final String USER_BRANCHES_CHANGED = "USER_BRANCHES_CHANGED";
   public static final String USER_LOCKED = "USER_LOCKED";
   public static final String USER_UNLOCKED = "USER_UNLOCKED";
   public static final String USER_DELETED = "USER_DELETED";
   public static final String USER_ACTIVATED = "USER_ACTIVATED";
   public static final String PASSWORD_RESET = "PASSWORD_RESET";
   ```

5. **Routes** (Need to Add to conf/routes)
   ```
   +nocsrf
   POST    /api/users                          controllers.UserController.create(request: Request)
   GET     /api/users                          controllers.UserController.list(request: Request)
   GET     /api/users/:id                      controllers.UserController.get(id: Integer, request: Request)
   +nocsrf
   PUT     /api/users/:id                      controllers.UserController.update(id: Integer, request: Request)
   +nocsrf
   PUT     /api/users/:id/role-branches        controllers.UserController.updateRoleBranches(id: Integer, request: Request)
   +nocsrf
   POST    /api/users/:id/lock                 controllers.UserController.lock(id: Integer, request: Request)
   +nocsrf
   POST    /api/users/:id/unlock               controllers.UserController.unlock(id: Integer, request: Request)
   +nocsrf
   DELETE  /api/users/:id                      controllers.UserController.delete(id: Integer, request: Request)
   +nocsrf
   POST    /api/users/:id/activate             controllers.UserController.activate(id: Integer, request: Request)
   +nocsrf
   POST    /api/users/:id/reset-password       controllers.UserController.resetPassword(id: Integer, request: Request)
   ```

6. **Authorization**
   - All endpoints require role AD (Admin)
   - Need to implement `@RequiresRole("AD")` or check in controller

7. **Tests** (Not Created)
   - Unit tests for UserService
   - Integration tests for UserController

## Next Steps
1. Fix JdbcUserRepository implementation (update signatures)
2. Add error codes and audit actions constants
3. Create UserController
4. Update routes
5. Add AuthModule bindings (if needed)
6. Write tests
7. Compile and run
8. Create test script test-phase4.ps1
9. Write PHASE4-REPORT.md

## Implementation Notes

### Password Policy (from config)
```hocon
password.policy {
  minLength = 8
  requireUppercase = true
  requireLowercase = true
  requireDigit = true
  requireSpecialChar = true
}
```

### Business Rules Implemented in UserService
- Username unique (case-insensitive, trimmed)
- Email unique (case-insensitive, trimmed)
- Cannot change username after creation
- Cannot lock/delete self
- Cannot lock/delete last active admin
- Reset failed login count when unlocking
- Revoke all tokens when: lock, delete, role/branch change, password reset
- Clear user status cache when: lock, unlock, delete, activate, role/branch change
- Audit log for all operations (max 1000 chars, không chứa password/token)

### Race Condition Handling
- Username/email duplicate: catch SQLException error code 2627/2601
- Return 409 Conflict with USER_USERNAME_EXISTS or USER_EMAIL_EXISTS

### Soft Delete
- DELETE /api/users/:id sets IsActive=0
- POST /api/users/:id/activate sets IsActive=1
- Không xóa vật lý user khỏi database

---

**Created:** 2026-11-26  
**Last Updated:** 2026-11-26  
**Progress:** 30% (DTOs + Service logic done, Controller + Tests pending)
