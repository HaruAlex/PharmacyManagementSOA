# VAI TRÒ
Bạn là Senior Backend Engineer. Nhiệm vụ: làm cho nền SOA (API Gateway + Consul) chạy ổn định, rồi tích hợp 5 service (Auth, Catalog, Purchase, Inventory, Sales) thành một hệ thống thống nhất cho "Hệ thống quản lý chuỗi nhà thuốc". Không viết lại từ đầu, chỉ sửa tối thiểu.

# MỤC TIÊU CHIẾN LƯỢC
Gateway và Consul là nền của kiến trúc. Sửa chúng cho đúng và cấu hình hóa hoàn toàn ngay bây giờ, để sprint sau thêm service mới (Report Service) chỉ cần: (1) gọi helper đăng ký Consul, (2) thêm một dòng route + route-role vào cấu hình. KHÔNG sửa code Gateway.

# PHẠM VI
Trong phạm vi: Gateway, Consul, Auth, Catalog, Purchase, Inventory, Sales.
Ngoài phạm vi (KHÔNG làm, KHÔNG tạo khung): Report Service.

# HIỆN TRẠNG
Gateway đã được xây dựng nhưng mới chỉ đăng ký được với Consul, còn một số lỗi và chưa đáp ứng yêu cầu SOA.
- KHÔNG viết lại Gateway, KHÔNG đổi công nghệ. Tự phát hiện công nghệ đang dùng (Ocelot/YARP/Spring Cloud Gateway/tự viết) và sửa trên nền đó. Chỉ đề xuất đổi nếu không thể đáp ứng yêu cầu bằng nền hiện tại, và phải hỏi tôi trước.
- Các service nghiệp vụ hiện chạy rời rạc, chưa kết nối với Auth.

# QUY TẮC LÀM VIỆC (BẮT BUỘC)
1. Làm tuần tự theo giai đoạn. **Không sang giai đoạn sau khi giai đoạn trước chưa qua cổng kiểm tra.**
2. Mỗi bước một commit (`fix(gateway): ...`, `fix(consul): ...`, `feat(integration): ...`). Chỗ nào sửa code của thành viên khác ghi vào `docs/CHANGELOG_INTEGRATION.md` (file, lý do).
3. Mọi cấu hình (secret, URL, port, connection string, route) nằm trong `appsettings`/biến môi trường/`.env`/file cấu hình route, không hard-code.
4. Dừng lại hỏi tôi nếu có quyết định ảnh hưởng nhiều service (đổi prefix route, schema, kiểu ID).
5. Sau mỗi giai đoạn báo cáo ngắn: đã làm gì, file nào đổi, cái gì chưa xong và vì sao, câu hỏi cần tôi quyết định.

# GIAI ĐOẠN 0: KHẢO SÁT (chỉ đọc, chưa sửa code)
- Xác định: công nghệ/framework từng thành phần, port, cấu trúc thư mục, endpoint hiện có, cách xác thực hiện tại, cách Gateway đăng ký Consul và cách cấu hình route.
- **Chạy thử thực tế** toàn bộ hệ thống (Consul + Gateway + các service) và tái hiện lỗi. Với mỗi lỗi ghi: triệu chứng, log/stack trace, nguyên nhân gốc.
- Kiểm tra sẵn các lỗi hay gặp:
  - Đăng ký Consul bằng `localhost`/`127.0.0.1` thay vì hostname container, sai address khi chạy Docker.
  - Health check URL Consul không gọi tới được (sai host/port, thiếu `GET /health`, trả mã không phải 200).
  - Trùng ServiceId giữa các instance cùng service; thiếu hủy đăng ký khi tắt; thiếu `DeregisterCriticalServiceAfter`.
  - Gateway có thật sự tra Consul hay vẫn dùng địa chỉ cứng; có chỉ lấy instance healthy hay không; có cập nhật khi instance thay đổi hay cache mãi.
  - Có round-robin thật không; khi instance chết có loại ra/thử instance khác không.
  - Xác thực JWT ở Gateway, forward header, CORS, timeout, định dạng lỗi.
- Xuất `docs/INTEGRATION_AUDIT.md` gồm 3 phần: (a) **Danh sách lỗi Gateway/Consul** kèm nguyên nhân gốc và mức ưu tiên; (b) bảng service/endpoint và các chỗ lệch nhau giữa 5 service (route, format lỗi, tên trường, kiểu ID, casing JSON, phân trang); (c) những gì còn thiếu so với yêu cầu bên dưới.
- Lập `docs/INTEGRATION_PLAN.md` (checklist). **Dừng, chờ tôi xác nhận trước khi sửa code.**

# GIAI ĐOẠN 1: SỬA GATEWAY + CONSUL (ƯU TIÊN CAO NHẤT)

## 1.1 Đăng ký/khám phá dịch vụ qua Consul
- Viết **một helper đăng ký Consul dùng chung** (shared library `Pharmacy.Common` hoặc tương đương): đọc `ServiceName`, `ServiceAddress`, `ServicePort`, `Consul:Address` từ cấu hình; ServiceId duy nhất cho mỗi instance (`name-hostname-port`); health check HTTP `GET /health` với interval/timeout hợp lý; `DeregisterCriticalServiceAfter`; tự hủy đăng ký khi service tắt.
- Áp helper cho Gateway và cả 5 service (thay các đoạn đăng ký riêng lẻ nếu có). Thêm `GET /health` cho service chưa có (không yêu cầu JWT).
- Địa chỉ đăng ký phải đúng khi chạy trong Docker (hostname container, không dùng localhost).
- Viết **service resolver dùng chung** (interface + cài đặt Consul): trả về instance healthy theo tên service, có cache ngắn hoặc blocking query, round-robin. Cả Gateway và các service gọi nhau đều dùng resolver này, không hard-code URL.

## 1.2 Gateway
- Route **hoàn toàn theo cấu hình** (file route), không hard-code địa chỉ: `/api/auth/*`, `/api/catalog/*`, `/api/purchase/*`, `/api/inventory/*`, `/api/sales/*`. Giữ prefix hiện có của Catalog nếu đã khác. Ghi vào `docs/ROUTING.md`.
- Chỉ route tới instance **healthy** lấy từ Consul; round-robin; instance lỗi bị loại; không còn instance nào thì trả 503 chuẩn.
- Xác thực JWT cho mọi request trừ `POST /api/auth/login` (và refresh nếu cần).
- Kiểm tra quyền thô theo **bảng route-role đặt trong cấu hình** (ai được vào prefix/method nào); kiểm tra `X-Branch-Id` phải thuộc `branch_ids` trong token, sai trả 403.
- Forward xuống service: `Authorization` (giữ nguyên), `X-User-Id`, `X-User-Role`, `X-Branch-Id`, `X-Request-Id` (tự sinh nếu thiếu).
- Lỗi chuẩn hóa `{ "success": false, "code": "...", "message": "...", "requestId": "..." }` cho 401/403/404/502/503/504 và lỗi validate.
- CORS cho frontend, timeout hợp lý, log request kèm `X-Request-Id`.

## 1.3 Làm sẵn cho sprint sau
- Viết `docs/ADD_NEW_SERVICE.md`: checklist đúng các bước để thêm một service mới (cấu hình đăng ký Consul, thêm route, thêm route-role, thêm `/health`, lấy JWT config).
- **Chứng minh** quy trình này bằng một stub service tạm đặt trong thư mục `tests/` (không đưa vào docker-compose chính, không phải Report Service): thêm nó chỉ bằng cấu hình, không sửa code Gateway, rồi gọi thành công qua Gateway.
- Viết script smoke test `scripts/smoke-gateway` (hoặc thư mục Postman riêng).

## CỔNG KIỂM TRA GIAI ĐOẠN 1 (phải đạt hết mới sang giai đoạn 2)
- [ ] `docker compose up` (Consul, SQL Server, Gateway, 5 service): mọi service "passing" trên Consul UI, không còn lỗi đăng ký.
- [ ] Gọi từng prefix qua Gateway tới đúng service.
- [ ] Chạy 2 instance cùng một service: request được chia round-robin; tắt 1 instance, request vẫn thành công; tắt hết, nhận 503 chuẩn.
- [ ] Không token -> 401; sai vai trò -> 403; `X-Branch-Id` sai -> 403; lỗi trả đúng định dạng chuẩn.
- [ ] Stub service ở 1.3 được thêm chỉ bằng cấu hình.
Báo cáo kết quả cổng kiểm tra cho tôi trước khi làm tiếp.

# GIAI ĐOẠN 2: JWT THỐNG NHẤT GIỮA CÁC SERVICE
- Auth là nơi DUY NHẤT phát hành JWT. Claims chuẩn: `sub/UserId`, `role` (AD|WH|SP), `branch_ids`, `exp`, `iss`, `aud`. Dùng chung một mục cấu hình Issuer/Audience/khóa cho Auth, Gateway, các service.
- Mỗi service nghiệp vụ vẫn validate JWT (defense in depth), trừ `/health` và Swagger.
- Middleware dùng chung: lấy user/role/branch từ token + header; dữ liệu WH/SP lọc theo chi nhánh hiện hành; AD xem toàn chuỗi.
- Phân quyền từng endpoint: AD toàn quyền quản trị; WH: nhập kho, chuyển kho, kiểm kê, hủy thuốc; SP: bán hàng, trả hàng, chốt ca, xem tồn/thuốc. Catalog: AD ghi, WH/SP chỉ đọc (khách hàng: AD + SP được ghi).
- Service-to-service: chuyển tiếp JWT của người dùng gốc kèm `X-Branch-Id`, `X-Request-Id`; địa chỉ đích qua resolver Consul.
- Ghi nhật ký thao tác nhạy cảm (sửa giá, hủy hóa đơn, điều chỉnh kho, đổi quyền) theo FR-AU-09: nếu Auth chưa có audit log thì thêm tối thiểu một endpoint nội bộ để các service gọi ghi.

# GIAI ĐOẠN 3: TÍCH HỢP NGHIỆP VỤ LIÊN SERVICE
Dùng HTTP client có timeout + retry (Polly hoặc tương đương).
1. **Catalog <-> các service khác:** Purchase/Inventory/Sales lấy thuốc, đơn vị quy đổi, nhà cung cấp, khách hàng, chi nhánh từ Catalog (cache ngắn được). Validate ID tồn tại trước khi ghi. Thiếu giá bán/tồn tối thiểu theo chi nhánh (FR-CN-02/03) thì ghi vào audit là "còn thiếu", không tự thêm.
2. **Purchase -> Inventory (FR-NH-02, FR-NH-04):** phiếu nhập chuyển "Approved" thì Purchase gọi Inventory tạo/cập nhật lô và cộng tồn chi nhánh nhận (số lô, NSX, hạn dùng, giá nhập, quy đổi về đơn vị nhỏ nhất). Idempotent (`ReferenceId` = mã phiếu nhập, gọi lại không cộng đôi). Trả hàng NCC (FR-NH-06) gọi Inventory trừ tồn.
3. **Sales -> Inventory (FR-BH-02, FR-BH-14, FR-KH-12):**
   - Hóa đơn "Đang xử lý" -> Inventory trừ tồn FEFO (trả danh sách lô + giá vốn) -> thành công thì "Hoàn tất", lưu giá bán, giá vốn, lô vào chi tiết hóa đơn (FR-BH-13).
   - Thất bại/timeout: hóa đơn "Thất bại/Hủy"; nếu đã trừ một phần thì gọi hoàn tồn (compensation). Không để "Hoàn tất" mà tồn chưa trừ.
   - Trả hàng/hủy hóa đơn -> Inventory hoàn tồn đúng lô đã xuất.
   - Chặn bán thuốc hết hạn; không bán vượt tồn khi nhiều thu ngân cùng lúc: transaction + khóa dòng (`UPDLOCK, ROWLOCK`) hoặc `UPDATE ... WHERE qty >= @n`.
   - Thuốc kê đơn bắt buộc có thông tin đơn (FR-BH-03): validate ở Sales, tra loại thuốc từ Catalog.
4. **Chuyển kho (FR-KH-04/05):** trừ tồn kho xuất ngay, trạng thái "Đang chuyển", kho nhận xác nhận số thực nhận, ghi chênh lệch; kiểm tra quyền theo chi nhánh xuất/nhận.
5. **Cảnh báo (FR-KH-09/10):** Inventory lấy ngưỡng cận date và tồn tối thiểu từ Catalog/cấu hình.

# GIAI ĐOẠN 4: CHUẨN HÓA, KIỂM THỬ, BÀN GIAO
- Chuẩn hóa: JSON camelCase, ngày ISO-8601 UTC, kiểu ID, phân trang (`page`, `pageSize`, `total`), envelope thành công/lỗi, mã HTTP. Mỗi service một database/schema riêng, không truy vấn chéo DB. Swagger có nút Authorize Bearer.
- `docker-compose.yml` hoàn chỉnh, `.env.example`, script migrate/seed (tài khoản AD/WH/SP, 2-3 chi nhánh gồm 1 kho tổng, vài thuốc, đơn vị quy đổi, nhà cung cấp).
- **Postman Collection** chạy qua Gateway (biến `baseUrl`, `token`, `branchId`; tự lưu token sau login). Kịch bản tối thiểu:
  1. Login AD/WH/SP, refresh, logout, sai mật khẩu nhiều lần bị khóa.
  2. Không token -> 401; sai vai trò -> 403; `X-Branch-Id` sai -> 403.
  3. AD tạo danh mục -> WH lập và duyệt phiếu nhập -> tồn tăng đúng theo lô.
  4. SP bán hàng: FEFO đúng lô cận hạn nhất, quy đổi đơn vị đúng, chặn hết hạn, chặn vượt tồn, thuốc kê đơn thiếu đơn bị từ chối.
  5. Trả hàng, hủy hóa đơn -> tồn hoàn đúng lô.
  6. Chuyển kho: xuất -> "đang chuyển" -> nhận thiếu -> ghi chênh lệch.
  7. Tắt Inventory rồi bán hàng -> hóa đơn không kẹt "Đang xử lý".
  8. `X-Request-Id` xuất hiện xuyên suốt log các service.
- Integration test tối thiểu: bán đồng thời cùng một lô (không âm tồn), idempotency duyệt phiếu nhập, compensation khi Inventory lỗi giữa chừng.
- Xuất `docs/INTEGRATION_REPORT.md` (tương ứng BE-47): sơ đồ kiến trúc (PlantUML), bảng route, sequence diagram cho 3 luồng (nhập hàng, bán hàng, chuyển kho), **danh sách lỗi Gateway/Consul đã sửa và nguyên nhân**, danh sách thay đổi, cách chạy, vấn đề tồn đọng, và mục "Sẵn sàng cho sprint sau" (trỏ tới `ADD_NEW_SERVICE.md`).

# TIÊU CHÍ HOÀN THÀNH TOÀN BỘ
- [ ] Cổng kiểm tra Giai đoạn 1 đạt.
- [ ] Mọi request từ client đi qua Gateway; không endpoint nghiệp vụ nào truy cập được khi thiếu JWT hợp lệ; phân quyền đúng theo vai trò và chi nhánh ở cả Gateway và service.
- [ ] Luồng nhập hàng -> tồn kho -> bán hàng (FEFO) -> trả hàng chạy end-to-end bằng Postman.
- [ ] Không còn URL/secret/route hard-code; thêm service mới chỉ bằng cấu hình.
- [ ] Tài liệu đầy đủ trong `docs/`.

Bắt đầu ngay bằng Giai đoạn 0. Đừng sửa code trước khi tôi xác nhận kế hoạch.