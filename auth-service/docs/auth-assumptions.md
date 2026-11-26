# Auth Service - Assumptions & Decisions

## Phase 1 - Nền tảng + Repository

### Database Connection
- Sử dụng HikariCP thông qua Play Framework default connection pool
- Connection string lấy từ environment variable `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`
- Blocking I/O được xử lý qua database dispatcher (thread pool riêng, 20 threads)

### Repository Layer
- Tất cả repository sử dụng JDBC thuần với PreparedStatement (không dùng ORM)
- Try-with-resources đảm bảo đóng connection đúng cách
- Sort whitelist cho UserRepository: `UserId`, `Username`, `FullName`, `Email`, `CreatedAt`, `UpdatedAt`
- Username comparison: case-insensitive (sử dụng `COLLATE Latin1_General_CI_AS`)
- Email comparison: case-insensitive (sử dụng `COLLATE Latin1_General_CI_AS`)

### Admin Dev Seeder
- **Chỉ chạy khi**: Play mode là Dev/Test HOẶC `seed.enableDevAdmin=true`
- **Production**: Seeder KHÔNG chạy (có kiểm tra `environment.isProd()`)
- Idempotent: 
  - Nếu user đã tồn tại với hash `REPLACE_WITH_BCRYPT_HASH` hoặc rỗng → cập nhật hash thật
  - Nếu user chưa có → tạo mới
  - Nếu user đã có hash hợp lệ → không làm gì
- Password mặc định cho dev:
  - `admin.demo` / `Admin@12345` - full access tất cả branches
  - `warehouse.demo` / `Warehouse@12345` - branches 1,2 (CN000, CN001)
  - `pharmacist.demo` / `Pharmacist@12345` - branch 2 (CN001)
- **CẢNH BÁO**: Mật khẩu này CHỈ dùng cho dev/test, KHÔNG BAO GIỜ dùng production

### BCrypt Configuration
- Cost factor: 11 (phù hợp cho production)
- Library: `at.favre.lib:bcrypt:0.10.2`

### Branch Lookup Client
- Dev/Test sử dụng `FakeBranchLookupClient` (in-memory), giả lập các branch ID 1, 2, 3 (CN000, CN001, CN002).
- Production sử dụng `HttpBranchLookupClient` qua Play WS, gọi `GET {catalog.service.baseUrl}{catalog.service.activeBranchesPath}`; path mặc định là `/api/branches/active`.
- Contract response là JSON có trường `branchIds` dạng mảng số nguyên, ví dụ `{"branchIds":[1,2,3]}`. Danh sách chỉ chứa branches đang active.
- HTTP status ngoài 2xx, response sai định dạng hoặc lỗi kết nối sẽ làm thao tác cần tra cứu thất bại rõ ràng; Auth Service không fallback sang fake trong Production.
- Catalog Service hiện chưa có route này. Cần triển khai endpoint theo contract trên ở Catalog trước khi xác nhận tích hợp Production.
- Tạo user và gán role/branches yêu cầu danh sách branch không rỗng, không trùng và mọi ID phải active. Khi cấp role `AD`, danh sách phải khớp toàn bộ active branch IDs.
- Chọn current branch kiểm tra cả quyền trong `auth.UserBranches` lẫn trạng thái active từ Catalog.

### Admin Role Branches
- Admin được gán TẤT CẢ branches đang active trong hệ thống; không chỉ kiểm tra danh sách có ít nhất một branch.

### AuditLog Details Truncation
- Maximum length: 1000 ký tự (theo schema)
- Nếu details vượt quá → cắt bớt an toàn (không throw exception)

### Clock Injection
- `java.time.Clock` được inject vào JWT issuer/verifier và các service cần kiểm soát thời gian để hỗ trợ clock giả trong test.
- Production sử dụng `Clock.systemUTC()`.

### Rate Limit
- `/api/auth/login`: tối đa 10 yêu cầu trên mỗi username/IP trong 15 phút; giới hạn theo IP là 100 yêu cầu trong cùng thời gian.
- `/api/auth/refresh`: tối đa 10 yêu cầu trên mỗi refresh token/IP và 30 yêu cầu trên mỗi IP trong 1 phút. Giới hạn IP giúp chặn việc né giới hạn bằng refresh token đã rotate.
- `/api/auth/change-password`: tối đa 10 yêu cầu trên mỗi user/IP trong 15 phút; giới hạn theo IP là 100 yêu cầu trong cùng thời gian.
- Các bộ đếm dùng Caffeine trong bộ nhớ của từng Auth Service instance; khóa được băm trước khi lưu. Đây không phải shared rate limit giữa nhiều instance.

### Error Handling
- SQL Exception code 2627/2601 = Unique constraint violation → xử lý riêng cho username/email trùng
- Tất cả repository methods ném exception nếu có lỗi (không nuốt exception)

### Test Strategy
- Unit test: mock repositories, không cần database thật
- Integration test: tag `@Tag("integration")`, yêu cầu SQL Server thật
- Play test framework: sử dụng `GuiceApplicationBuilder.overrides()` để thay repository bằng fake

### JWT Secret Validation (Phase 2)
- Production: Nếu thiếu `JWT_SECRET` hoặc < 32 bytes → app KHÔNG khởi động
- Dev: Có giá trị default nhưng log cảnh báo

---

## Decisions Log

### 2024 - Phase 1
- **Decision**: Giữ Fake Branch Client cho Dev/Test và dùng HTTP Branch Client trong Production
  - **Reason**: Dev/Test không phụ thuộc Catalog Service; Production tra cứu trạng thái branch thật
  - **Impact**: Catalog Service phải cung cấp endpoint theo contract đã ghi ở trên

- **Decision**: Seeder chạy eager singleton, không phải manual trigger
  - **Reason**: Đơn giản hóa setup, dev chỉ cần start app là có tài khoản test
  - **Impact**: Cần careful check Production mode

- **Decision**: Repository methods không tự xử lý transaction, để caller quyết định
  - **Reason**: Linh hoạt hơn, service layer kiểm soát transaction boundary
  - **Exception**: `replaceBranches()` và `replacePermissions()` cần atomic nên có transaction built-in

- **Decision**: Email không có UNIQUE constraint trong DB, kiểm tra ở application layer
  - **Reason**: Schema đã có sẵn không có UNIQUE cho Email
  - **Impact**: Race condition có thể xảy ra, nhưng ít nghiêm trọng hơn username

---

## Open Questions (cần xác nhận)
- None (Phase 1 hoàn tất)
