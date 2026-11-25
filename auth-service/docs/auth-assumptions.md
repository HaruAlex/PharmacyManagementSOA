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
- Phase 1 sử dụng `FakeBranchLookupClient` (in-memory)
- Giả lập 3 branches: ID 1,2,3 (CN000, CN001, CN002)
- Phase sau sẽ implement `HttpBranchLookupClient` gọi Catalog Service qua Play WS

### Admin Role Branches
- Admin được gán TẤT CẢ branches đang active trong hệ thống
- Khi tạo/cập nhật admin, cần đảm bảo có ít nhất 1 branch

### AuditLog Details Truncation
- Maximum length: 1000 ký tự (theo schema)
- Nếu details vượt quá → cắt bớt an toàn (không throw exception)

### Clock Injection
- Inject `java.time.Clock` để có thể mock trong test
- Production sử dụng `Clock.systemUTC()`

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
- **Decision**: Sử dụng Fake Branch Client thay vì HTTP client ngay từ đầu
  - **Reason**: Giảm dependency vào Catalog Service, test dễ hơn
  - **Impact**: Cần implement HTTP client ở phase sau

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
