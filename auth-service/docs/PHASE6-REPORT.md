# Báo cáo Phase 6 — RBAC và Audit Log

**Lần xác minh gần nhất:** 2026-11-26  
**Trạng thái:** Đã triển khai; compile cục bộ, unit test và API check đã pass. Đây không phải xác nhận hoàn thành Phase 7 hoặc sẵn sàng Production.

## Phạm vi đã hoàn thành

- Role APIs: tạo, liệt kê, xem và cập nhật; role code được chuẩn hóa và không thể thay đổi. Không cho vô hiệu hóa role đang có user active (trả `409`).
- Permission APIs: tạo, liệt kê, xem và cập nhật; permission code được chuẩn hóa và không thể thay đổi.
- Role-permission APIs: liệt kê và thay thế permissions trong một database transaction. Role `AD` không thể mất quyền `USER_MANAGE`. Khi thay đổi permissions, cache trạng thái user liên quan được xóa và refresh token của họ bị thu hồi.
- RBAC và audit endpoints sử dụng `@HasPermission("USER_MANAGE")`.
- Audit search hỗ trợ lọc theo user ID, action code, entity name, time range, pagination và sort order. Audit write được tập trung, timestamp theo UTC, cắt `Details` ở 1000 ký tự và log lỗi repository mà không làm hỏng nghiệp vụ chính.

## Ảnh database và lỗi đăng nhập

Ảnh database được cung cấp cho thấy `warehouse.demo` và `pharmacist.demo` bị khóa với `FailedLoginCount = 5`. Trạng thái này khiến login từ chối các tài khoản, nhưng không giải thích HTTP 500: tài khoản bị khóa cần được trả về như lỗi authentication/business.

Đã sửa hai nguyên nhân xử lý lỗi có thể dẫn đến 500:

- Global error handler unwrap `CompletionException`/`ExecutionException` trước khi ánh xạ business error.
- `TokenInvalidException` được ánh xạ thành `401 AUTH_TOKEN_INVALID` thay vì 500.

Development/test seeder khôi phục trạng thái active/unlocked và reset failed-login count cho demo account mà không ghi đè password hash hợp lệ. Seeder không chạy ở Production; đây không phải cách sửa dữ liệu database Production.

## Kết quả kiểm thử đã ghi nhận

| Kiểm tra | Kết quả |
|---|---|
| `sbt compile` | Pass |
| `sbt test` (lần xác minh hiện tại) | Pass: 36 JUnit 5 tests; sbt báo 2 JUnit 4 tests pass, 0 fail |
| `test-scripts/test-phase6.ps1` với service/database cục bộ | 23 pass, 0 fail |
| Bearer token không hợp lệ trên protected endpoint | HTTP 401, `AUTH_TOKEN_INVALID` |

API script sử dụng role/permission code riêng và tạo dữ liệu test trong database cục bộ. Script không xóa các row đó; không nên giả định database vẫn sạch sau khi chạy.

## Giới hạn đã biết và công việc tiếp theo

- Role và permission response DTO hiện trả `null` cho `createdAt`/`updatedAt` vì các trường này chưa được ánh xạ trong domain entity.
- Các kiểm tra trên bao phủ local API flow của Phase 6 và unit test hiện có; không thay thế yêu cầu Phase 7 về end-to-end, coverage, Postman, tài liệu và security acceptance trong `yeu-cau-sprint1.md`.
- API run cục bộ không chứng minh Production readiness. Cần xác minh riêng production configuration, schema/data migration, secrets, rate limits, CORS/security headers và deployment behavior.
