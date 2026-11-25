# VAI TRÒ
Bạn là Senior Backend Engineer (Java). Hãy xây dựng **Auth Service** cho hệ thống "Pharmacy Management SOA" (kiến trúc SOA, mỗi service sở hữu schema riêng trong cùng 1 database SQL Server `PharmacyManagementSOA`).

# NGUYÊN TẮC LÀM VIỆC
1. Làm **theo từng đợt (Phase)** ở mục "KẾ HOẠCH". Hết mỗi đợt phải: `sbt compile` thành công, `sbt test` pass toàn bộ, rồi báo cáo ngắn (file đã tạo/sửa, API đã xong, kết quả test, giả định đã đặt ra) và **DỪNG, chờ tôi xác nhận** mới sang đợt tiếp theo.
2. Không tự ý sửa `01_create_tables.sql`. Nếu bắt buộc phải đổi schema, tạo file mới `03_auth_patch.sql` (idempotent) và giải thích lý do. Ưu tiên không cần patch.
3. Không có FK liên service: `auth.UserBranches.BranchId` chỉ là số nguyên. Tên/trạng thái chi nhánh lấy từ Catalog Service qua interface `BranchLookupClient` (viết bản HTTP client dùng Play WS và bản fake/in-memory cho test và dev).
4. Code sạch, tách tầng: Controller -> Service -> Repository. Dùng Guice DI (constructor injection, `@Inject`, `@Singleton`). Không hardcode secret; dùng `application.conf` + biến môi trường (`${?JWT_SECRET}`).
5. Nếu thiếu thông tin, đặt giả định hợp lý, ghi vào `docs/auth-assumptions.md` rồi tiếp tục, không hỏi lan man.

# CÔNG NGHỆ (nếu repo đã có stack thì ưu tiên theo repo)
- **Java 17**, **Play Framework 3.0.x** (Pekko; nếu repo đang dùng Play 2.9 / Akka thì theo repo), build bằng **sbt** (plugin `PlayJava`).
- SQL Server: driver `com.microsoft.sqlserver:mssql-jdbc`, dùng `play.db.Database` (HikariCP của Play, cấu hình trong `application.conf`) với **JDBC thuần + PreparedStatement** (schema đã có sẵn bằng SQL script, không dùng Ebean/JPA/Evolutions). Tắt Evolutions (`play.evolutions.enabled=false`).
- JDBC là blocking: cấu hình một dispatcher riêng `database.dispatcher` (thread-pool cố định) trong `application.conf`; controller chạy logic service/repository trong dispatcher này thông qua `CompletableFuture.supplyAsync(..., dbExecutionContext)`, tuyệt đối không chặn thread mặc định của Play. Service và Repository để dạng đồng bộ cho dễ test.
- Hash mật khẩu: `at.favre.lib:bcrypt` (cost >= 11), bọc sau interface `PasswordHasher`.
- JWT: `com.auth0:java-jwt` (HS256, key từ config), bọc sau interface `TokenService`.
- JSON: Jackson qua `play.libs.Json`. Validation: Jakarta Bean Validation (Hibernate Validator) kết hợp validator thủ công cho rule nghiệp vụ (password policy...).
- Cache: `play-caffeine-cache` (`SyncCacheApi`) cho cache trạng thái user và rate limit.
- HTTP client gọi Catalog Service: Play WS (`javaWs`).
- Test: **JUnit 5 + Mockito + AssertJ** cho unit test thuần; test chức năng/tích hợp dùng `play.test` (`WithApplication`/`WithServer`, `Helpers.fakeRequest`, `GuiceApplicationBuilder.overrides(...)` để thay Repository bằng bản in-memory; nếu `play.test.WithApplication` yêu cầu JUnit 4 ở phiên bản đang dùng thì dùng JUnit Vintage engine cho riêng nhóm test này và ghi chú lại). Không bắt buộc có SQL Server thật để chạy unit test. Integration test với SQL Server thật gắn tag `@Tag("integration")` và chạy riêng.
- Coverage: `sbt-jacoco`.
- Cấu trúc project (một ứng dụng Play, tách theo package):
  ```
  auth-service/
    app/
      controllers/        (AuthController, UserController, RoleController, PermissionController, AuditLogController, HealthController)
      actions/            (JwtAuthAction, @Authenticated, @HasPermission + action composition, RateLimitFilter)
      dto/                (Request/Response DTO, PagedResult<T>)
      domain/             (entity, enum, constant: AuditActions, ErrorCodes, RoleCodes)
      services/           (AuthService, UserService, ProfileService, RbacService, AuditService, interface + impl)
      repositories/       (interface UserRepository, RoleRepository, PermissionRepository, UserBranchRepository, RefreshTokenRepository, AuditLogRepository)
      repositories/jdbc/  (implement bằng JDBC)
      security/           (PasswordHasher, TokenService, RefreshTokenGenerator, UserStatusCache)
      clients/            (BranchLookupClient, HttpBranchLookupClient, FakeBranchLookupClient)
      seed/               (AdminDevSeeder)
      errors/             (ApiException, ErrorHandler implements HttpErrorHandler -> ProblemDetails)
      modules/            (AuthModule: Guice bindings)
    conf/ (application.conf, application.test.conf, routes, logback.xml)
    test/ (unit/, functional/, fakes/)
    postman/AuthService.postman_collection.json
    docs/ (auth-assumptions.md)
    README.md
  ```

# BỐI CẢNH DATABASE (schema `auth`, đã có)
- `auth.Roles(RoleId, RoleCode, RoleName, IsActive)` – seed: `AD`, `WH`, `SP`.
- `auth.Permissions(PermissionId, PermissionCode, PermissionName)`.
- `auth.RolePermissions(RoleId, PermissionId)`.
- `auth.Users(UserId, RoleId, Username UNIQUE, PasswordHash, FullName, Phone, Email, IsLocked, FailedLoginCount, IsActive, CreatedAt, UpdatedAt)`.
- `auth.UserBranches(UserId, BranchId)`.
- `auth.RefreshTokens(RefreshTokenId, UserId, TokenHash UNIQUE, ExpiresAt, RevokedAt, CreatedAt)`.
- `auth.AuditLogs(AuditLogId, UserId, BranchId, ActionCode, EntityName, EntityId, Details, CreatedAt)`.

**Lưu ý quan trọng:** dữ liệu seed `02_seed_data.sql` dùng `PasswordHash = 'REPLACE_WITH_BCRYPT_HASH'` nên **không đăng nhập được**. Xem Phase 1 để xử lý.

# QUY ƯỚC CHUNG
- Base route: `/api/auth/...`, `/api/users/...`, `/api/roles/...`, `/api/permissions/...`, `/api/audit-logs/...` (khai báo trong `conf/routes`).
- Response lỗi thống nhất (RFC 7807 ProblemDetails) kèm trường `code` (vd: `AUTH_INVALID_CREDENTIALS`, `AUTH_ACCOUNT_LOCKED`, `AUTH_REFRESH_EXPIRED`, `USER_USERNAME_EXISTS`...). Cài `ErrorHandler` (HttpErrorHandler) để gom mọi exception về định dạng này; exception nghiệp vụ dùng `ApiException(status, code, message)`.
- Response phân trang: `{ items, page, pageSize, totalItems, totalPages }`.
- Validation trả 400 kèm danh sách lỗi theo field.
- Không bao giờ trả `PasswordHash`, `TokenHash` hoặc log mật khẩu/token ra ngoài.
- Thông báo đăng nhập sai dùng chung 1 message cho "sai username" và "sai mật khẩu" (chống dò tài khoản).
- Phân quyền endpoint bằng action composition: `@Authenticated` (bắt buộc JWT hợp lệ), `@HasPermission("...")`, hoặc kiểm tra role. Admin = role `AD`.
- Giờ dùng UTC; inject `java.time.Clock` để mock trong test. Không gọi `Instant.now()` trực tiếp trong service.

# CƠ CHẾ PHIÊN ĐĂNG NHẬP (ĐÃ CHỐT, ÁP DỤNG XUYÊN SUỐT)
| Thành phần | Thời hạn | Vai trò |
|---|---|---|
| Access token (JWT) | 15 phút (`jwt.accessTokenMinutes`) | Gọi API |
| Refresh token | **Cố định 7 ngày kể từ lúc đăng nhập** (`jwt.refreshTokenDays`) | Tự xin access token mới |

- Khi access token hết hạn, Client tự gọi `/api/auth/refresh`; server cấp access token mới và **xoay vòng** refresh token (revoke token cũ, cấp token mới). Người dùng không nhận ra.
- Token mới sinh ra khi rotation **giữ nguyên `ExpiresAt` của token gốc** (không gia hạn). Hết 7 ngày kể từ lần đăng nhập thì refresh trả 401 `AUTH_REFRESH_EXPIRED` và bắt buộc đăng nhập lại, dù người dùng vẫn hoạt động.
- Tài khoản bị khóa / vô hiệu hóa: revoke toàn bộ refresh token ngay lúc đó; lần refresh kế tiếp thất bại, Client xóa token và về màn hình đăng nhập. Access token còn hạn bị chặn bởi bước kiểm tra trạng thái user (cache tối đa 10 giây, được xóa chủ động khi khóa/đổi role).

# KẾ HOẠCH THEO ĐỢT

## PHASE 1 – Nền tảng + Repository + tài khoản Admin test (BE-02)
- Khởi tạo project Play theo cấu trúc trên, cấu hình connection string (env var, không commit secret), dispatcher DB, health check `GET /health` (kiểm tra kết nối DB), `ErrorHandler`, `Clock` binding, `AuthModule` (Guice).
- Viết các Repository (interface + implement JDBC dùng `Database`/`PreparedStatement`, try-with-resources, đóng connection đúng):
  - `UserRepository`: getById, getByUsername, getByEmail, getByUsernameOrEmail, existsUsername, existsEmail, search (lọc + phân trang `OFFSET/FETCH`), create, update, setLocked, updatePasswordHash, incrementFailedLogin, resetFailedLogin, setActive.
  - `RoleRepository`, `PermissionRepository` (gồm lấy permission theo role).
  - `UserBranchRepository`: getBranchIds(userId), replaceBranches(userId, branchIds) trong **1 transaction** (`database.withTransaction`).
  - `RefreshTokenRepository`: create, getByHash, revoke, revokeAllByUser, deleteExpired.
  - `AuditLogRepository`: write, search.
- Mọi truy vấn dùng tham số hóa (chống SQL injection); `ORDER BY` chỉ nhận cột trong whitelist.
- **Tài khoản Admin để test** (seed SQL không đăng nhập được):
  - Viết `AdminDevSeeder` (eager singleton bind trong module), **chỉ chạy khi Play mode là Dev/Test** hoặc khi `seed.enableDevAdmin=true`. Production tuyệt đối không chạy (có test).
  - Cấu hình trong `application.conf`: `seed.admin.username=admin.demo`, `seed.admin.email=admin@example.local`, `seed.admin.password=Admin@12345` (chỉ là mật khẩu dev, ghi rõ trong README là KHÔNG dùng production).
  - Idempotent: nếu `admin.demo` đã tồn tại với hash `REPLACE_WITH_BCRYPT_HASH` hoặc rỗng thì cập nhật bằng BCrypt hash thật; nếu chưa có thì tạo mới (đảm bảo role AD tồn tại) và gán toàn bộ chi nhánh hiện có (qua `BranchLookupClient`, hoặc ở Dev đọc `catalog.Branches`). Đồng thời đặt `IsLocked=0, FailedLoginCount=0, IsActive=1`.
  - Tuỳ chọn: seed thêm `warehouse.demo` (WH) và `pharmacist.demo` (SP) cùng cơ chế để test phân quyền.
- Unit test: seeder (tạo mới, cập nhật hash placeholder, idempotent, không chạy ở Prod), whitelist sort, validate tham số repository ở mức có thể mock; integration test repository (tuỳ chọn, có tag).
- Bàn giao: project chạy được (`sbt run`), `GET /health` OK, admin đăng nhập được ở Phase 2.

## PHASE 2 – Đăng nhập, JWT, khóa tài khoản (BE-03, BE-04, BE-05)
- `POST /api/auth/login` body `{ usernameOrEmail, password }` (đăng nhập bằng Username HOẶC Email).
- Quy trình: tìm user -> `IsActive=1` -> `IsLocked=0` -> role còn `IsActive` -> verify BCrypt. Sai mật khẩu thì tăng `FailedLoginCount`; đạt ngưỡng (`auth.maxFailedAttempts`, mặc định 5) thì `IsLocked=1`, ghi audit `ACCOUNT_AUTO_LOCKED`. Đăng nhập đúng thì reset `FailedLoginCount=0`. Tài khoản bị khóa trả 423 `AUTH_ACCOUNT_LOCKED`, kể cả khi nhập đúng mật khẩu (chỉ Admin mở khóa).
- Chống timing attack: khi không tìm thấy user vẫn thực hiện 1 phép BCrypt verify giả.
- JWT (HS256, key từ `JWT_SECRET`, tối thiểu 32 byte; thiếu key ở Prod thì app **không khởi động**): claim gồm `sub` (UserId), `role` (RoleCode), `branch_ids` (mảng BranchId), `current_branch_id` (nếu có), `jti`, `iat`, `exp`. Thời hạn 15 phút. Có thể kèm permission code nếu gọn (ghi quyết định về kích thước token vào assumptions).
- Response login: `{ accessToken, expiresIn, refreshToken, user: { userId, username, fullName, role, branchIds, permissions } }`. Refresh token được phát hành ở Phase 3; Phase 2 chỉ để interface `RefreshTokenIssuer` và bản tạm.
- **Xử lý token đã phát hành** (yêu cầu 2.6.1): không mặc định JWT còn hạn là còn hiệu lực. Trong `@Authenticated` (JwtAuthAction), sau khi verify chữ ký/hạn thì kiểm tra trạng thái user (`IsActive`, `IsLocked`, `RoleId` có đổi so với claim không) qua `UserStatusCache`:
  - Cache tối đa **10 giây** (`auth.userStatusCacheSeconds`, đặt 0 để kiểm tra mọi request).
  - **Chủ động xóa cache của user** khi khóa, vô hiệu hóa, đổi role/branch, đổi quyền role, đổi mật khẩu, để có hiệu lực ngay.
  - Token không còn hợp lệ trả 401 `AUTH_TOKEN_REVOKED`.
- `GET /api/auth/validate`: cho Gateway/service khác kiểm tra token + trạng thái user (introspection, trả `{ active, userId, role, branchIds, currentBranchId }`). Ghi tài liệu giới hạn: cache chỉ cục bộ theo instance, độ trễ tối đa 10 giây.
- `GET /api/auth/me`: thông tin user hiện tại.
- Audit: `LOGIN_SUCCESS`, `LOGIN_FAILED`, `ACCOUNT_AUTO_LOCKED` (không ghi mật khẩu).
- Unit test bắt buộc: đăng nhập đúng bằng username; bằng email; sai mật khẩu tăng counter; đủ ngưỡng thì khóa; khóa + đúng mật khẩu vẫn bị từ chối; user inactive; role inactive; user không tồn tại (cùng message với sai mật khẩu); reset counter khi thành công; JWT chứa đủ UserId/Role/BranchIds và hết hạn đúng (dùng `Clock` giả); token hết hạn/sai chữ ký bị từ chối; token của user vừa bị khóa bị từ chối (cache = 0 và cache có xóa chủ động); đổi RoleId thì token cũ bị từ chối.

## PHASE 3 – Refresh token, đăng xuất, chọn chi nhánh (BE-06, BE-52)
- Refresh token là chuỗi ngẫu nhiên an toàn (`SecureRandom`, >= 64 byte, Base64Url), **chỉ lưu hash SHA-256 vào `TokenHash`**. Hết hạn **cố định** sau `jwt.refreshTokenDays` (mặc định 7) kể từ lúc đăng nhập: khi **rotation, token mới copy nguyên `ExpiresAt` của token cũ**.
- Nếu phát hiện dùng lại token đã revoke (reuse) thì revoke toàn bộ token của user (nghi ngờ bị đánh cắp) và ghi audit `REFRESH_TOKEN_REUSE`.
- `POST /api/auth/refresh` body `{ refreshToken }`: kiểm tra token tồn tại, chưa revoke, chưa hết hạn (nếu hết hạn trả 401 `AUTH_REFRESH_EXPIRED`), user còn `IsActive` và không `IsLocked`; cấp access token mới (đọc lại role/branch mới nhất từ DB) + refresh token mới (rotation, giữ nguyên ExpiresAt).
- `POST /api/auth/logout` body `{ refreshToken }` (đã xác thực): revoke refresh token ở server, trả 204; xóa cache trạng thái user nếu phù hợp. `POST /api/auth/logout-all`: revoke mọi token của user. Ghi chú rõ: xóa hoàn toàn Session/JWT phía Client là trách nhiệm Client, server chỉ vô hiệu hóa refresh token; access token sống ngắn (15 phút). Khi refresh thất bại (hết hạn, revoke, khóa) Client phải xóa token và chuyển về trang đăng nhập (ghi vào README).
- `PUT /api/auth/current-branch` body `{ branchId }`: chỉ cho chọn chi nhánh có trong `auth.UserBranches` của user (Admin cũng theo danh sách đã gán); kiểm tra chi nhánh còn hoạt động qua `BranchLookupClient`. Lưu bằng cách phát hành **access token mới** chứa `current_branch_id` (không cần cột mới); ghi audit `BRANCH_SELECTED`. Refresh về sau phải **giữ lại** `current_branch_id` nếu chi nhánh đó vẫn còn trong danh sách gán. Nếu user chỉ có 1 chi nhánh thì login tự chọn luôn.
- Phương thức `RefreshTokenService.cleanupExpired()` dọn token hết hạn (không bắt buộc chạy nền; có thể đăng ký scheduler của Play tùy chọn).
- Unit test: refresh hợp lệ; **refresh nhiều lần không kéo dài ExpiresAt**; sau mốc 7 ngày refresh trả 401 `AUTH_REFRESH_EXPIRED`; đã revoke; reuse detection; user bị khóa thì refresh thất bại; logout revoke đúng token; logout-all; chọn chi nhánh hợp lệ / không thuộc quyền / chi nhánh inactive; token mới chứa `current_branch_id`; refresh giữ lại branch hiện hành.

## PHASE 4 – Quản lý tài khoản (BE-07, BE-50)
Chỉ role `AD` được gọi nhóm API này.
- `POST /api/users`: tạo mới `{ fullName, email, phone, roleId, username, password, branchIds }`. Username duy nhất toàn hệ thống (so sánh không phân biệt hoa thường, trim), Email nếu có cũng nên duy nhất (kiểm tra ở tầng ứng dụng vì schema không có unique cho Email). Chính sách mật khẩu: >= 8 ký tự, có chữ hoa, chữ thường, số, ký tự đặc biệt (config được). Hash BCrypt. Trả 409 `USER_USERNAME_EXISTS` khi trùng; xử lý cả race condition khi vi phạm UNIQUE ở DB (bắt `SQLException` mã 2627/2601).
- `GET /api/users`: danh sách phân trang (`page`, `pageSize` tối đa 100), lọc theo `name` (FullName/Username chứa), `email`, `roleId`/`roleCode`, `status` (`active`/`locked`/`inactive`), sắp xếp whitelist.
- `GET /api/users/:id`: chi tiết gồm role và branchIds.
- `PUT /api/users/:id`: cập nhật `fullName, email, phone, roleId`. **Cấm đổi Username**: DTO không có field username (và có test xác nhận request gửi kèm username thì không làm thay đổi).
- `PUT /api/users/:id/role-branches` (BE-50): gán role + danh sách chi nhánh (replace trong 1 transaction, kiểm tra branchId hợp lệ qua `BranchLookupClient`; ít nhất 1 chi nhánh, ghi rõ quy ước cho role AD trong assumptions). Khi đổi: revoke toàn bộ refresh token của user, xóa cache trạng thái, audit `USER_ROLE_CHANGED`, `USER_BRANCHES_CHANGED`.
- `POST /api/users/:id/lock` và `POST /api/users/:id/unlock`: đổi `IsLocked`. Unlock thì reset `FailedLoginCount=0`. Lock thì revoke toàn bộ refresh token + xóa cache trạng thái. Admin không được tự khóa chính mình; không được khóa/hạ quyền Admin cuối cùng của hệ thống. Ghi audit.
- `DELETE /api/users/:id`: **không xóa vật lý**, thực hiện xóa mềm `IsActive=0` (revoke token, xóa cache). Cung cấp `POST /api/users/:id/activate` để kích hoạt lại. Nêu rõ trong tài liệu.
- `POST /api/users/:id/reset-password`: Admin đặt lại mật khẩu (nhận mật khẩu mới hoặc sinh mật khẩu tạm), revoke token, audit.
- Unit test: tạo thành công; trùng username (khác hoa thường); trùng email; mật khẩu yếu; role không tồn tại; phân trang/lọc từng điều kiện; cập nhật không đổi được username; gán role/branch + revoke token; lock/unlock; chặn tự khóa; chặn khóa Admin cuối; xóa mềm; người không phải Admin nhận 403.

## PHASE 5 – Hồ sơ cá nhân và đổi mật khẩu (BE-48, BE-49)
- `GET /api/auth/profile` và `PUT /api/auth/profile`: user tự cập nhật `fullName, phone, email` (không được đổi role, username, branch). Kiểm tra email trùng người khác.
- `PUT /api/auth/change-password` body `{ currentPassword, newPassword, confirmNewPassword }`: xác thực mật khẩu hiện tại (sai nhiều lần phải bị giới hạn: tính vào cơ chế chống brute force hoặc rate limit riêng), mật khẩu mới đạt policy, khác mật khẩu cũ, khớp confirm. Thành công thì cập nhật hash, set `UpdatedAt`, revoke các refresh token (ghi rõ trong assumptions: revoke tất cả hay chỉ các token khác), xóa cache trạng thái, audit `PASSWORD_CHANGED`.
- Unit test: cập nhật hồ sơ hợp lệ; email trùng; cố đổi field cấm; đổi mật khẩu thành công; sai mật khẩu cũ; mật khẩu mới yếu / trùng cũ / confirm không khớp; token bị revoke sau đổi mật khẩu.

## PHASE 6 – RBAC và Audit log (BE-51, BE-53)
- RBAC (chỉ `AD`):
  - Role: `GET/POST/PUT /api/roles`, `GET /api/roles/:id`; không xóa vật lý, dùng `IsActive`; không cho vô hiệu hóa role đang có user active (409); `RoleCode` duy nhất và bất biến sau khi tạo.
  - Permission: `GET/POST/PUT /api/permissions`; `PermissionCode` duy nhất; không xóa khi đang được gán (409).
  - RolePermission: `GET /api/roles/:id/permissions`, `PUT /api/roles/:id/permissions` (replace trong 1 transaction). Role `AD` luôn giữ đủ quyền quản trị (chặn gỡ `USER_MANAGE` khỏi AD).
  - Khi đổi quyền của role: xóa cache trạng thái/quyền của các user thuộc role đó, revoke refresh token của họ, ghi audit.
  - Action composition `@HasPermission("USER_MANAGE")` đọc permission từ DB/cache, áp dụng cho các controller đã làm. Có thể thay kiểm tra role `AD` ở Phase 4 bằng permission tương ứng mà không phá test cũ.
- Audit log:
  - `AuditService.write(...)` dùng thống nhất cho mọi thao tác nhạy cảm: đăng nhập thành công/thất bại, khóa/mở khóa, tạo/sửa/xóa mềm user, đổi role/branch, reset/đổi mật khẩu, đổi RBAC, chọn chi nhánh, reuse refresh token. Lưu `UserId` (người thực hiện), `BranchId` hiện hành, `ActionCode`, `EntityName`, `EntityId`, `Details` (tối đa 1000 ký tự, cắt an toàn, **không chứa mật khẩu/token/hash**), kèm IP/User-Agent trong Details.
  - Lỗi ghi audit không được làm hỏng nghiệp vụ chính (try/catch + log), trừ thao tác bắt buộc phải có audit thì ghi chú rõ.
  - `GET /api/audit-logs` (Admin): lọc theo userId, actionCode, entityName, khoảng thời gian, phân trang.
- Hằng số `AuditActions` tập trung, không dùng magic string rải rác.
- Unit test: CRUD role/permission; chặn trùng code; chặn vô hiệu hóa role đang dùng; replace permission; bảo vệ quyền của AD; `@HasPermission` cho phép/từ chối; audit được ghi đúng ở từng thao tác nhạy cảm; audit không chứa dữ liệu nhạy cảm; lọc audit.

## PHASE 7 – Kiểm thử tổng thể, Postman, tài liệu (BE-08)
- Functional test end-to-end với Play test (repository in-memory qua `overrides`): login -> gọi API có token -> refresh -> chọn chi nhánh -> logout -> refresh token cũ bị từ chối; khóa sau 5 lần sai; Admin tạo user mới rồi user đó đăng nhập; Admin khóa user thì access token cũ bị chặn và refresh thất bại; sau 7 ngày (Clock giả) refresh trả `AUTH_REFRESH_EXPIRED`.
- Báo cáo coverage (jacoco), mục tiêu >= 80% cho tầng services.
- Postman Collection `postman/AuthService.postman_collection.json` + environment file: biến `baseUrl`, `accessToken`, `refreshToken`, script test tự lưu token sau login; có request cho mọi endpoint kèm test script kiểm tra status code và schema; thư mục theo nhóm (Login/Session, Profile, Users, RBAC, Audit); dùng tài khoản `admin.demo` ở Phase 1.
- Viết `README.md`: cách chạy (chạy script SQL 01, 02, đặt biến môi trường `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `sbt run`, seeder dev), bảng endpoint + role/permission cần có, ví dụ request/response, danh sách mã lỗi, cấu hình JWT/lockout/cache, cách chạy test, tài khoản test + cảnh báo bảo mật, quy trình đăng nhập qua Gateway theo mục 2.6.1, giới hạn đã biết (độ trễ thu hồi access token tối đa theo cache 10 giây, cache cục bộ theo instance).
- Rà soát bảo mật cuối: không lộ hash/secret; **rate limit cho `/login`, `/refresh`, `/change-password`** (Play Filter/Action dùng Caffeine, theo IP + username); CORS (`play.filters.cors`), header bảo mật (`play.filters.headers`), HTTPS ở Prod, log không chứa dữ liệu nhạy cảm; `play.http.secret.key` lấy từ env.

# YÊU CẦU CHẤT LƯỢNG CHO TOÀN BỘ TEST
- Tên test dạng `method_condition_expectedResult`, mỗi test độc lập, dùng `Clock`/`PasswordHasher`/`RefreshTokenGenerator` giả để kiểm soát thời gian và sinh token.
- Mỗi rule nghiệp vụ ở trên phải có ít nhất 1 test dương và 1 test âm.
- `sbt compile` và `sbt test` phải pass trước khi báo cáo mỗi đợt; test lỗi phải sửa, không được xóa hoặc bỏ qua.

# ĐỊNH DẠNG BÁO CÁO CUỐI MỖI ĐỢT
1. Tóm tắt đã làm (mã BE-xx tương ứng).
2. Danh sách file mới/sửa.
3. Endpoint đã có (method, route, role/permission).
4. Kết quả `sbt test` (số test, pass/fail, coverage nếu có).
5. Giả định / vấn đề cần tôi quyết định.
6. Cách chạy thử nhanh (lệnh + tài khoản test).

**Bắt đầu với PHASE 1.** Chưa làm các Phase sau cho đến khi tôi xác nhận.