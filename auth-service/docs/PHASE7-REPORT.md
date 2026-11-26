# BÁO CÁO PHASE 7 – KIỂM THỬ TỔNG THỂ, POSTMAN, TÀI LIỆU

## 1. Tóm tắt

Đã bổ sung các hạng mục Phase 7 và xác minh build/test/coverage. Test suite pass và service line coverage vượt ngưỡng; collection Postman đã được cấu trúc/route kiểm tra nhưng chưa chạy bằng Postman/Newman.

- BE-08: thêm Play functional test với repository in-memory, Postman collection/environment, README hiện trạng và cấu hình hardening.
- Bộ Postman có 33 request cho toàn bộ endpoint API và health; không tính route phục vụ static assets.
- JaCoCo được cấu hình chỉ đo package `services` với ngưỡng line coverage 80%.

## 2. File mới/sửa

- Mới: `test/controllers/AuthPhase7EndToEndTest.java` – 5 functional test độc lập và adapter repository in-memory.
- Mới: `test/services/ProfileServiceTest.java`, `test/services/UserServiceTest.java` – kiểm thử nghiệp vụ profile, mật khẩu và quản lý user.
- Sửa: `test/services/RbacServiceTest.java`, `test/services/AuditServiceTest.java` – mở rộng nhánh kiểm thử RBAC và audit.
- Sửa: `project/plugins.sbt` – thêm sbt-jacoco.
- Sửa: `build.sbt` – giới hạn coverage vào package `services`, ngưỡng line 80%.
- Sửa: `conf/application.conf` – bật CORS với allowlist rỗng mặc định và cấu hình security headers.
- Mới: `conf/application.prod.conf` – yêu cầu DB/JWT/Play secrets qua biến môi trường và bật HTTPS redirect.
- Mới: `postman/AuthService.postman_collection.json`, `postman/AuthService.postman_environment.json`.
- Mới: `README.md` – hướng dẫn chạy, endpoints, biến môi trường, bảo mật và kiểm thử.

## 3. Endpoint

Postman bao phủ toàn bộ route API trong `conf/routes`: Health; Login/Session; Profile; Users; RBAC; Audit. Route static `/assets/*file` không thuộc API nghiệp vụ và không được đưa vào collection.

Endpoint quản trị Users/RBAC/Audit cần role AD và permission `USER_MANAGE`; các API hồ sơ cần đăng nhập; login/refresh và health công khai. `/api/auth/validate` cho phép gọi không token để trả `valid=false`.

## 4. Kiểm thử và coverage

- `sbt compile test jacoco`: pass.
- JUnit 5 wrapper tìm thấy và chạy thành công 69/69 test; trong đó có 5 Play E2E test Phase 7.
- JaCoCo package `services`: line coverage **88.39%** (586/663 dòng), vượt yêu cầu >= 80%; branch coverage 56.48%.
- Báo cáo HTML: `target/scala-2.13/jacoco/report/html/index.html`.
- Đã xác thực JSON và script Postman parse được; 33 request khớp 33 route API/health (không tính static assets).
- Collection Postman chưa được thực thi trong Postman Desktop/Newman; việc kiểm tra JSON/JavaScript không thay thế chạy request live.
- Health endpoint trả HTTP 200, database `connected`, khi server được chạy từ PowerShell.

## 5. Vấn đề còn lại

Build, test và ngưỡng service coverage đã đạt. Nếu cần xác nhận execution của Postman collection, import collection/environment vào Postman Desktop và chạy trên database dev/test; collection có các thao tác tạo/cập nhật dữ liệu demo.

## 6. Chạy thử

Tại thư mục `auth-service` chạy:

```powershell
sbt compile
sbt test
sbt jacoco
```

Postman: import `postman/AuthService.postman_collection.json` và `postman/AuthService.postman_environment.json`, chọn `Auth Service - Local`, rồi chạy các folder theo thứ tự trên database dev/test dùng được. Tài khoản mẫu: `admin.demo` / `Admin@12345`; chỉ dùng trong dev/test.
