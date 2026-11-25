# BÁO CÁO PHASE 2 - Auth Service

## 1. Tóm tắt đã làm

Hoàn thành **PHASE 2 – Đăng nhập, JWT, Khóa tài khoản** theo yêu cầu BE-03, BE-04, BE-05:

✅ POST /api/auth/login (đăng nhập bằng username HOẶC email)  
✅ JWT access token (HS256, 15 phút) với claims đầy đủ  
✅ Khóa tự động sau 5 lần nhập sai password  
✅ User status cache (10 giây, có thể clear chủ động)  
✅ GET /api/auth/validate (token introspection)  
✅ GET /api/auth/me (thông tin user hiện tại)  
✅ Chống timing attack (BCrypt verify giả khi user không tồn tại)  
✅ Audit log: LOGIN_SUCCESS, LOGIN_FAILED, ACCOUNT_AUTO_LOCKED  
✅ Error Handler với RFC 7807 Problem Details format  
✅ Refresh token placeholder (sẽ implement thật ở Phase 3)  

## 2. Danh sách file mới/sửa

### File mới tạo (Phase 2)

**Security layer** (4 files):
- `app/security/TokenService.java` - Interface cho JWT service
- `app/security/JwtTokenService.java` - Implementation JWT với java-jwt
- `app/security/TokenInvalidException.java` - Exception cho token không hợp lệ
- `app/security/UserStatusCache.java` - Cache trạng thái user (Caffeine, 10s)

**Service layer** (1 file):
- `app/services/AuthService.java` - Business logic cho authentication (430 lines)
  - Login với username hoặc email
  - Verify password với BCrypt
  - Kiểm tra IsActive, IsLocked, Role active
  - Tăng FailedLoginCount và auto-lock sau 5 lần
  - Reset FailedLoginCount khi login thành công
  - Chống timing attack
  - Tự động chọn branch nếu chỉ có 1
  - Ghi audit log

**Controller layer** (1 file):
- `app/controllers/AuthController.java` - HTTP endpoints (thay thế file cũ)
  - POST /api/auth/login
  - GET /api/auth/validate
  - GET /api/auth/me

**DTO layer** (4 files):
- `app/dto/LoginRequest.java` - Request DTO cho login
- `app/dto/LoginResponse.java` - Response DTO cho login (với UserInfo nested class)
- `app/dto/TokenValidateResponse.java` - Response cho validate endpoint
- `app/dto/UserMeResponse.java` - Response cho /me endpoint

**Error handling** (2 files):
- `app/errors/ErrorHandler.java` - Global error handler (RFC 7807)
- `app/errors/ApiException.java` - Business exception với status + code

**Test scripts** (2 files):
- `test-scripts/test-phase2.ps1` - Automated test script (10 test cases)
- `test-scripts/README.md` - Hướng dẫn chạy test

**Documentation** (1 file):
- `PHASE2-REPORT.md` - Báo cáo này

### File đã sửa

- `app/modules/AuthModule.java` - Thêm bindings: TokenService, không bind ErrorHandler (dùng config)
- `conf/application.conf` - Thêm: `play.http.errorHandler = "errors.ErrorHandler"`
- `conf/routes` - Thêm 3 routes mới cho auth endpoints
- `build.sbt` - Sửa validation dependencies (Hibernate Validator 6.x thay vì 8.x)

## 3. Endpoint đã có

| Method | Route | Auth | Mô tả |
|---|---|---|---|
| POST | `/api/auth/login` | Public | Đăng nhập bằng username/email + password |
| GET | `/api/auth/validate` | Optional Bearer | Token introspection (Gateway/services) |
| GET | `/api/auth/me` | Bearer required | Thông tin user hiện tại |
| GET | `/health` | Public | Health check (Phase 1) |

## 4. Kết quả test

### Compile

```
[info] compiling 3 Scala sources and 12 Java sources
[info] done compiling
[success] Total time: 8 s
```

✅ **Compile thành công**

### Manual Test

```powershell
# Test 1: Login thành công
PS> $response = Invoke-RestMethod -Uri "http://localhost:9001/api/auth/login" `
      -Method POST -ContentType "application/json" `
      -Body '{"usernameOrEmail":"admin.demo","password":"Admin@12345"}'

✅ Response:
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 900,
  "refreshToken": "refresh_token_placeholder",
  "user": {
    "userId": 1,
    "username": "admin.demo",
    "fullName": "Quản trị hệ thống",
    "roleCode": "AD",
    "roleName": "Quản trị viên",
    "branchIds": [1, 2, 3],
    "currentBranchId": null,
    "permissions": ["USER_MANAGE", "CATALOG_MANAGE", ...]
  }
}
```

### Automated Test Script

```powershell
PS> cd auth-service/test-scripts
PS> .\test-phase2.ps1

============================================
  TEST SUMMARY - PHASE 2
============================================
Total Tests:  10
Passed:       10
Failed:       0
============================================

✅ ALL TESTS PASSED - PHASE 2 HOÀN THÀNH!
```

**10 test cases:**
1. ✅ Đăng nhập thành công với username
2. ✅ Đăng nhập thành công với email
3. ✅ GET /api/auth/me
4. ✅ GET /api/auth/validate
5. ✅ Sai password 5 lần → auto-lock (423)
6. ✅ Đúng password nhưng đã lock → vẫn 423
7. ✅ Token không hợp lệ → 401
8. ✅ Thiếu Authorization header → 401
9. ✅ Validate token không hợp lệ → active=false
10. ✅ JWT chứa đầy đủ claims

### Database Verification

```sql
-- Audit logs đã ghi
SELECT TOP 10 ActionCode, Details, CreatedAt 
FROM auth.AuditLogs 
ORDER BY CreatedAt DESC;

✅ Kết quả:
ActionCode              | Details                           | CreatedAt
------------------------|-----------------------------------|-------------------
LOGIN_SUCCESS           | Successful login, IP: ...         | 2024-11-25 23:50:00
LOGIN_FAILED            | Wrong password (attempt 5/5)      | 2024-11-25 23:49:50
ACCOUNT_AUTO_LOCKED     | Account auto-locked after 5...    | 2024-11-25 23:49:50
LOGIN_FAILED            | Wrong password (attempt 4/5)      | 2024-11-25 23:49:48
...

-- User đã bị lock
SELECT Username, IsLocked, FailedLoginCount 
FROM auth.Users 
WHERE Username = 'pharmacist.demo';

✅ Kết quả:
Username         | IsLocked | FailedLoginCount
-----------------|----------|------------------
pharmacist.demo  | 1        | 5
```

## 5. Giả định / quyết định kỹ thuật (Phase 2)

### 1. JWT Structure
- **Algorithm**: HS256 (symmetric)
- **Claims**: 
  - `sub` = userId (string)
  - `role` = roleCode (string)
  - `branch_ids` = array of integer
  - `current_branch_id` = integer or null
  - `jti` = UUID (token ID)
  - `iat`, `exp` = standard
- **Không lưu permissions trong JWT** vì sẽ làm token quá lớn, permissions query từ DB khi cần

### 2. Refresh Token (Phase 2)
- Trả về placeholder string `"refresh_token_placeholder"`
- Phase 3 sẽ implement:
  - Sinh chuỗi ngẫu nhiên 64 bytes
  - Lưu hash SHA-256 vào DB
  - Rotation khi refresh
  - Giữ nguyên ExpiresAt khi rotation

### 3. User Status Cache
- Cache 10 giây (config: `auth.userStatusCacheSeconds`)
- Chứa: `isActive`, `isLocked`, `roleId`, `roleCode`
- **Chủ động xóa** khi: lock/unlock, đổi role, đổi password, vô hiệu hóa
- **Limitation**: Cache local per-instance, độ trễ tối đa 10 giây

### 4. Failed Login Counter
- Tăng mỗi lần sai password
- Reset về 0 khi login thành công
- Đạt ngưỡng (default 5) → `IsLocked = 1` và ghi audit `ACCOUNT_AUTO_LOCKED`
- Khi locked, dù nhập đúng password vẫn trả 423 Locked

### 5. Chống Timing Attack
- Khi user không tồn tại, vẫn thực hiện BCrypt verify với dummy password
- Đảm bảo response time tương tự cho "user không tồn tại" và "sai password"
- Message lỗi dùng chung: "Invalid username, email, or password"

### 6. Error Response Format (RFC 7807)
```json
{
  "status": 401,
  "code": "AUTH_INVALID_CREDENTIALS",
  "message": "Invalid username, email, or password",
  "path": "/api/auth/login"
}
```

### 7. Audit Log Details
- Tối đa 1000 ký tự, tự động truncate
- Chứa IP, User-Agent (không chứa password/token)
- Lỗi ghi audit không fail request chính (try/catch + log)

### 8. Auto-select Branch
- Nếu user chỉ có 1 branch → tự động set `current_branch_id` trong JWT
- Nếu có nhiều branches → `current_branch_id = null`, client tự chọn sau (Phase 3)

### 9. JWT Secret Validation
- Production: Nếu thiếu `JWT_SECRET` hoặc < 32 bytes → app KHÔNG khởi động
- Dev: Cho phép default secret nhưng log WARNING

### 10. Database Dispatcher
- Tất cả DB operations chạy trong `dbContext` (thread pool riêng)
- Controller method return `CompletionStage<Result>` với `CompletableFuture.supplyAsync(..., dbContext.current())`

## 6. Hướng dẫn test lại

### Bước 1: Start service

```powershell
cd auth-service

# Set env
$env:DATABASE_URL="jdbc:sqlserver://localhost:1433;databaseName=PharmacyManagementSOA;encrypt=false;trustServerCertificate=true"
$env:DATABASE_USER="sa"
$env:DATABASE_PASSWORD="123456"
$env:JWT_SECRET="dev-secret-key-min-32-bytes-for-testing-only"

# Run
sbt run
```

### Bước 2: Chạy test script

```powershell
cd auth-service/test-scripts
.\test-phase2.ps1
```

### Bước 3: Manual test (optional)

```powershell
# Login
$response = Invoke-RestMethod -Uri "http://localhost:9001/api/auth/login" `
  -Method POST -ContentType "application/json" `
  -Body '{"usernameOrEmail":"admin.demo","password":"Admin@12345"}'

$token = $response.accessToken

# Get user info
Invoke-RestMethod -Uri "http://localhost:9001/api/auth/me" `
  -Headers @{"Authorization" = "Bearer $token"} | ConvertTo-Json
```

### Bước 4: Unlock tài khoản test (nếu cần)

```sql
-- Unlock pharmacist.demo sau khi test
UPDATE auth.Users 
SET IsLocked = 0, FailedLoginCount = 0 
WHERE Username = 'pharmacist.demo';
```

## 7. Known Issues & Limitations

### 1. Cache là local per-instance
- Mỗi instance có cache riêng
- Khi scale nhiều instances → độ trễ sync có thể lên đến `cacheSeconds * số instances`
- Giải pháp tương lai: Redis cache shared

### 2. Refresh token chưa implement
- Phase 2 chỉ trả placeholder
- Phase 3 sẽ implement đầy đủ: rotation, expiry, reuse detection

### 3. Rate limiting chưa có
- Phase 7 sẽ thêm rate limit cho /login, /refresh, /change-password
- Hiện tại có thể brute force (đã có auto-lock nhưng chưa rate limit)

### 4. Token revocation chưa hoàn chỉnh
- Hiện tại chỉ check user status (active, locked, role)
- Chưa có blacklist token khi logout
- Phase 3 sẽ implement refresh token revocation

## 8. Next Steps - Phase 3

**PHASE 3 – Refresh token, Logout, Chọn chi nhánh (BE-06, BE-52)**

Sẽ implement:
- ✅ POST /api/auth/refresh (rotation, giữ nguyên ExpiresAt)
- ✅ POST /api/auth/logout (revoke refresh token)
- ✅ POST /api/auth/logout-all (revoke tất cả tokens)
- ✅ PUT /api/auth/current-branch (chọn chi nhánh hiện hành)
- ✅ Refresh token: chuỗi ngẫu nhiên 64 bytes, lưu hash SHA-256
- ✅ Reuse detection: revoke all tokens nếu dùng lại token đã revoke
- ✅ Cleanup expired tokens (scheduler hoặc manual)

---

**Ngày hoàn thành**: 25/11/2024  
**Trạng thái**: ✅ HOÀN TẤT - Sẵn sàng Phase 3  
**Test coverage**: 10/10 test cases passed
