# Auth Service

Dịch vụ xác thực, quản lý phiên đăng nhập và phân quyền cho Pharmacy Management SOA.

## Yêu cầu

- JDK 17 trở lên
- sbt 1.9 trở lên (repo hiện dùng sbt 1.13.1)
- SQL Server 2019 trở lên; đã chạy `database/scripts/01_create_tables.sql` và `database/scripts/02_seed_data.sql`
- Catalog Service chỉ cần khi chạy production; môi trường dev/test dùng Fake Branch Lookup Client

## Chạy local

`application.conf` dùng SQL Server local và tài khoản dev mặc định để tiện chạy thử. Nên ghi đè thông tin DB bằng biến môi trường:

```powershell
$env:DATABASE_URL = "jdbc:sqlserver://localhost:1433;databaseName=PharmacyManagementSOA;encrypt=false;trustServerCertificate=true"
$env:DATABASE_USER = "sa"
$env:DATABASE_PASSWORD = "<mật khẩu SQL Server>"
$env:JWT_SECRET = "<chuỗi ngẫu nhiên tối thiểu 32 byte>"
$env:APPLICATION_SECRET = "<chuỗi ngẫu nhiên riêng cho Play>"
Set-Location auth-service
sbt run
```

Mặc định server lắng nghe cổng `9001`. Kiểm tra:

```powershell
Invoke-RestMethod http://localhost:9001/health
```

Seeder dev tự tạo hoặc khôi phục ba tài khoản mẫu, trạng thái và chi nhánh mỗi lần khởi động trong Dev/Test. Không sử dụng các tài khoản/mật khẩu này ở môi trường production.

| Username | Mật khẩu dev/test | Role | Chi nhánh |
|---|---|---|---|
| `admin.demo` | `Admin@12345` | AD | Tất cả chi nhánh giả lập |
| `warehouse.demo` | `Warehouse@12345` | WH | CN000, CN001 |
| `pharmacist.demo` | `Pharmacist@12345` | SP | CN001 |

## Cấu hình production

Chạy qua TLS termination proxy tin cậy; proxy phải đặt `X-Forwarded-Proto: https`. Bắt buộc khai báo các biến môi trường bên dưới, không dùng giá trị mẫu:

```powershell
$env:DB_URL = "jdbc:sqlserver://<host>:1433;databaseName=PharmacyManagementSOA;encrypt=true;trustServerCertificate=false"
$env:DB_USER = "<user>"
$env:DB_PASSWORD = "<secret>"
$env:JWT_SECRET = "<secret ngẫu nhiên tối thiểu 32 byte>"
$env:APPLICATION_SECRET = "<secret ngẫu nhiên riêng cho Play>"
Set-Location auth-service
sbt "-Dconfig.file=conf/application.prod.conf" run
```

`application.prod.conf` yêu cầu đủ secret và thông tin DB khi khởi động, bật HTTPS redirect/HSTS. CORS bị từ chối mặc định; chỉ thêm origin frontend chính xác vào `play.filters.cors.allowedOrigins` trong cấu hình triển khai. Không dùng `*` cùng credentials. `JWT_SECRET` và `APPLICATION_SECRET` phải khác nhau, được cấp từ secret manager và xoay vòng theo quy trình vận hành.

## API

Các endpoint JSON nằm dưới `/api`. Endpoint quản trị yêu cầu access token của role `AD` và permission `USER_MANAGE`. Các route xác thực còn lại yêu cầu bearer token trừ health, login, refresh và validate (validate không có token sẽ trả `valid=false`).

| Nhóm | Method và route | Quyền |
|---|---|---|
| Health | `GET /health`, `GET /health/detailed` | Công khai |
| Login/session | `POST /api/auth/login`, `POST /api/auth/refresh` | Công khai |
| Login/session | `GET /api/auth/validate` | Công khai; token tùy chọn |
| Login/session | `POST /api/auth/logout`, `POST /api/auth/logout-all`, `GET /api/auth/me`, `PUT /api/auth/current-branch` | Đăng nhập |
| Profile | `GET /api/auth/profile`, `PUT /api/auth/profile`, `PUT /api/auth/change-password` | Đăng nhập |
| Users | `POST /api/users`, `GET /api/users`, `GET /api/users/:id`, `PUT /api/users/:id`, `PUT /api/users/:id/role-branches`, `POST /api/users/:id/lock`, `POST /api/users/:id/unlock`, `DELETE /api/users/:id`, `POST /api/users/:id/activate`, `POST /api/users/:id/reset-password` | AD + `USER_MANAGE` |
| RBAC | `GET/POST /api/roles`, `GET/PUT /api/roles/:id`, `GET/PUT /api/roles/:id/permissions` | AD + `USER_MANAGE` |
| RBAC | `GET/POST /api/permissions`, `GET/PUT /api/permissions/:id` | AD + `USER_MANAGE` |
| Audit | `GET /api/audit-logs` | AD + `USER_MANAGE` |

Ví dụ đăng nhập và gọi endpoint cần token:

```http
POST /api/auth/login
Content-Type: application/json

{"usernameOrEmail":"admin.demo","password":"Admin@12345"}
```

Response thành công chứa `accessToken` và `refreshToken`; gửi access token tiếp theo bằng:

```http
GET /api/auth/me
Authorization: Bearer <accessToken>
```

`POST /api/auth/refresh` nhận `{"refreshToken":"..."}` và trả cặp token mới. Refresh token cũ bị thu hồi khi xoay vòng/đăng xuất; access token dùng JWT ngắn hạn và kiểm tra trạng thái user theo cache cục bộ.

### Đăng nhập qua Gateway (mục 2.6.1)

Client gửi thông tin đăng nhập tới route auth của API Gateway. Gateway chuyển tiếp tới Auth Service, nhận access/refresh token và trả phản hồi cho client. Client gửi access token ở header `Authorization: Bearer ...` cho các request tiếp theo; refresh token chỉ dùng với route refresh, không gửi như bearer token. Không ghi token vào log hoặc URL.

### Mã lỗi thường gặp

Lỗi JSON có `status`, `code`, `message`. Các mã thường gặp: `VALIDATION_FAILED`, `AUTH_INVALID_CREDENTIALS`, `AUTH_ACCOUNT_LOCKED`, `AUTH_ACCOUNT_INACTIVE`, `AUTH_TOKEN_INVALID`, `AUTH_TOKEN_REVOKED`, `AUTH_REFRESH_INVALID`, `AUTH_REFRESH_REVOKED`, `AUTH_REFRESH_EXPIRED`, `FORBIDDEN`, `USER_NOT_FOUND`, `USER_USERNAME_EXISTS`, `USER_EMAIL_EXISTS`, `ROLE_NOT_FOUND`, `ROLE_INACTIVE`, `ROLE_CODE_EXISTS`, `PERMISSION_NOT_FOUND`, `PERMISSION_CODE_EXISTS`, `PASSWORD_WEAK`, `RATE_LIMIT_EXCEEDED`.

## Bảo mật và giới hạn đã biết

- Access token sống 15 phút; refresh token tối đa 7 ngày. Refresh token được lưu dạng hash.
- Login, refresh và change-password có rate limit Caffeine theo IP và danh tính, tính riêng trên từng instance.
- Cache trạng thái/quyền user là cục bộ, nên thu hồi access token có thể trễ tối đa 10 giây và không đồng bộ giữa các instance.
- Mỗi instance giữ limiter/cache trong bộ nhớ; triển khai nhiều instance cần gateway rate limit bổ sung.
- Branch Lookup dùng fake data trong Dev/Test; Production gọi Catalog qua `CATALOG_SERVICE_URL`.
- Không đưa secrets, mật khẩu, token, password hash hoặc nội dung nhạy cảm vào log, Postman export hay báo cáo.
- Production cần TLS, secret manager, CORS allowlist chính xác và SQL Server encryption; các thiết lập mẫu trong `application.conf` chỉ dành cho local dev.

## Kiểm thử và coverage

```powershell
Set-Location auth-service
sbt compile
sbt test
sbt jacoco
```

JaCoCo giới hạn báo cáo vào package `services` và đặt ngưỡng line coverage 80%. Báo cáo HTML nằm trong `target/scala-2.13/jacoco/report/`.

Postman collection và environment: [postman/AuthService.postman_collection.json](postman/AuthService.postman_collection.json), [postman/AuthService.postman_environment.json](postman/AuthService.postman_environment.json). Import cả hai file, chọn environment, rồi chạy folder theo thứ tự; request đăng nhập tự lưu token.

## Tài liệu

- [Giả định và quyết định kỹ thuật](docs/auth-assumptions.md)
- [Yêu cầu Sprint 1](../yeu-cau-sprint1.md)
- [Báo cáo Phase 7](docs/PHASE7-REPORT.md)
