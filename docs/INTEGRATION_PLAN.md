# Integration Plan

**Trạng thái:** Kế hoạch Giai đoạn 0 đã được xác nhận; đang triển khai Giai đoạn 1. Chưa bắt đầu Giai đoạn 2.

## Giai đoạn 0 — Khảo sát

- [x] Đọc `INTEGRATION_PROMPT.md`, khảo sát repo/cấu hình/routes/logs.
- [x] Chạy Consul, Gateway, Auth, Catalog, Purchase, Inventory, Sales bằng SBT; kiểm tra `/health`, registry Consul và proxy thực tế.
- [x] Ghi lỗi tái hiện, nguyên nhân và các lệch API vào `docs/INTEGRATION_AUDIT.md`.
- [x] Lập checklist này.
- [x] Không sửa code. Auth dev seeder đã chạy với sự cho phép của người dùng.
- [x] **Cổng:** Người dùng đã xác nhận kế hoạch và cho phép bắt đầu Giai đoạn 1.

## Các quyết định cần chốt trước khi triển khai

- [x] **Prefix public:** đã được duyệt: `/api/auth`, `/catalog`, `/api/purchase`, `/api/inventory`, `/api/sales`; giữ `/catalog` và ánh xạ các prefix còn lại tới route nội bộ hiện hành.
- [ ] **Database/schema:** hiện năm service cùng dùng `PharmacyManagementSOA`. Chốt phương án tách database riêng hay giữ một database nhưng tách schema; xác nhận tên database/schema và kế hoạch migration/backup trước khi thay đổi.
- [ ] **ID contract:** Auth dùng `Integer`, các service còn lại trộn `Long`/`Integer`/`String`. Chốt kiểu ID chuẩn và cách migrate/serialize; đề xuất dùng số nguyên 64-bit cho ID nghiệp vụ mới và chuyển đổi có tương thích ngược, nhưng không tự áp dụng khi chưa duyệt.
- [ ] **Auth dev seed:** khi chạy `sbt run`, Auth seed tạo/cập nhật demo accounts. Lần khảo sát đã được cho phép; trước các lần chạy có seed sau này, xác nhận DB đích/backup và có thể cấu hình chế độ kiểm thử tách biệt.

## Giai đoạn 1 — Gateway + Consul (ưu tiên cao nhất)

- [x] **Consul registration helper** dùng chung: cấu hình service name/address/port/Consul, ID duy nhất theo instance/hostname/port, `/health`, interval/timeout, deregister và shutdown lifecycle thật.
- [x] Áp helper cho Gateway và đúng năm service trong phạm vi; dùng hostname/container address và runtime port; các ứng dụng có health endpoint không yêu cầu JWT.
- [x] **Resolver chung** dùng Consul health API (`passing`), cache TTL ngắn và thread-safe round-robin; Gateway cùng Auth/Catalog và Sales/Inventory client dùng resolver.
- [x] Đưa service routing và role rules ra cấu hình; giữ đúng prefix đã được duyệt và ghi contract vào `docs/ROUTING.md`.
- [x] Gateway giữ method, path/query, body, cần thiết headers và upstream status; retry loại instance đã lỗi; Gateway-generated/no-instance errors có envelope 503.
- [x] Thêm JWT validation tại Gateway (trừ POST login/refresh), role/method rules theo cấu hình, branch claim/header validation và identity/request header forwarding.
- [x] Chuẩn hóa Gateway errors, cấu hình CORS theo public prefixes, timeout và stable request ID.
- [x] Bổ sung focused tests cho failover, no-instance, CORS preflight, resolver round-robin, route config (bao gồm profile stub), JWT/branch, và method/query/body/status/header forwarding.
- [x] Ghi thay đổi và giới hạn còn lại trong `docs/CHANGELOG_INTEGRATION.md`.
- [x] Thêm fixture stub trong `tests/gateway-stub/` và profile chỉ cấu hình route/role; chưa chạy fixture thực tế.
- [ ] Tạo một commit cho mỗi bước sửa độc lập theo quy tắc dự án (ví dụ `fix(consul): ...`, `fix(gateway): ...`).
- [ ] **Cổng Giai đoạn 1:** chưa đạt. Theo khảo sát, repo không có Compose file và Docker/Compose không khả dụng; chưa chạy được full-stack/2-instance Consul failover hoặc fixture stub. Cần xác minh các gate này trong môi trường có Consul/Compose trước Giai đoạn 2.

Các API contract không khớp của Auth–Catalog và Sales–Inventory vẫn được giữ nguyên trong Giai đoạn 1; việc sửa business routes/payloads thuộc các giai đoạn tích hợp sau.

## Giai đoạn 2 — JWT và phân quyền đồng bộ

- [ ] Chốt và cấu hình chung `issuer`, `audience`, key/secret; Auth là nơi duy nhất phát hành JWT với claims chuẩn.
- [ ] Validate JWT ở từng service nghiệp vụ (trừ health/Swagger); middleware chuẩn hóa user/role/branch; kiểm soát dữ liệu theo branch.
- [ ] Áp role policy theo endpoint đã nêu; xác nhận mọi route không thể truy cập khi thiếu token hợp lệ.
- [ ] Service-to-service chuyển tiếp JWT gốc, `X-Branch-Id`, `X-Request-Id`; target qua resolver.
- [ ] Ghi audit cho thao tác nhạy cảm theo FR-AU-09.
- [ ] Test Auth login/refresh/logout, invalid/expired token, role, branch và request ID.

## Giai đoạn 3 — Tích hợp nghiệp vụ

- [ ] Thêm HTTP client có timeout/retry phù hợp; không retry thao tác ghi nếu chưa idempotent.
- [ ] Purchase/Inventory/Sales lấy thuốc, đơn vị, supplier/customer/branch từ Catalog; validate ID trước khi ghi; ghi nhận riêng các yêu cầu giá/tồn tối thiểu còn thiếu.
- [ ] Purchase receipt được duyệt -> Inventory tạo/cập nhật lô và tồn idempotent; supplier return trừ tồn.
- [ ] Sales invoice -> Inventory FEFO/deduct; lưu lô và giá vốn; xử lý timeout/lỗi/compensation; return/cancel hoàn đúng lô.
- [ ] Đảm bảo chống oversell bằng transaction/conditional update; không bán thuốc hết hạn; validate prescription khi bắt buộc.
- [ ] Hoàn thiện stock transfer xuất/nhận/chênh lệch và quyền theo branch.
- [ ] Inventory lấy ngưỡng cảnh báo từ Catalog/cấu hình.
- [ ] Integration tests cho concurrency, idempotency và compensation.

## Giai đoạn 4 — Chuẩn hóa, kiểm thử, bàn giao

- [ ] Chuẩn hóa JSON camelCase, ngày UTC ISO-8601, ID, pagination, response envelope và HTTP status.
- [ ] Tách database/schema theo phương án đã duyệt; không truy vấn chéo DB.
- [ ] Hoàn thiện Compose, `.env.example`, migrations/seeds; không đưa secret vào repo.
- [ ] Postman collection chạy qua Gateway với biến môi trường và chuỗi nghiệp vụ tối thiểu theo prompt.
- [ ] Thêm smoke test Gateway; thêm stub trong `tests/` chỉ bằng cấu hình, không sửa code Gateway; không tạo Report Service.
- [ ] Xuất `docs/ADD_NEW_SERVICE.md`, `docs/ROUTING.md`, `docs/INTEGRATION_REPORT.md` (sơ đồ, routes, sequence nhập/bán/chuyển, lỗi đã sửa, chạy hệ thống, tồn đọng, sprint sau).
- [ ] Chạy toàn bộ cổng kiểm tra; báo cáo kết quả cuối. Mỗi bước sửa tạo commit riêng, không gộp commit của người khác.

## Điều kiện bắt đầu

- Giai đoạn 0 đã hoàn tất; người dùng đã xác nhận kế hoạch và prefix public.
- Giai đoạn 1 đang triển khai; các kiểm thử đơn vị/tích hợp cục bộ đã chạy,
  nhưng cổng runtime yêu cầu Consul/Compose vẫn chưa đạt.
- Chưa bắt đầu Giai đoạn 2; cần xác minh full-stack, hai-instance failover và
  gọi stub thật qua Gateway trước.
