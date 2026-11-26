# Integration Audit — Giai đoạn 0

**Ngày khảo sát:** 2026-11-26
**Phạm vi:** API Gateway, Consul, Auth, Catalog, Purchase, Inventory, Sales.
**Ngoài phạm vi:** Report Service (không khởi động, không thay đổi).

## 1. Kết quả chạy thử

### Môi trường và cách chạy

- Backend là Java + Play Framework 3.0.12, Scala 2.13.18, build/chạy bằng SBT 1.13.1. Lần chạy khảo sát dùng Java 25.0.2.
- SQL Server đang nghe ở `localhost:1433`. Các cấu hình mặc định của năm service dùng cùng database `PharmacyManagementSOA`.
- Docker không cài đặt và repo không có `docker-compose.yml`; do đó kiểm thử chạy cục bộ, không phải kiểm thử container.
- Consul 2.1.0 được chạy ở chế độ dev, dùng data directory tạm ngoài repo. Gateway và năm service được chạy bằng `sbt "run <port>"`.
- Auth chạy ở Dev mode; seeder đã tạo/cập nhật tài khoản demo. Người dùng đã cho phép thao tác này. Không có credential nào được ghi vào tài liệu này.
- Log khởi động xác nhận cả 6 ứng dụng đã biên dịch/chạy; SBT phát cảnh báo một số API deprecated. Lần biên dịch đầu mất thời gian, trong thời gian đó health probe timeout; sau khi ứng dụng hoàn tất startup, health trả 200.

### Kết quả kiểm tra trực tiếp

| Thành phần | URL | Kết quả |
|---|---|---|
| Consul | `GET /v1/status/leader` | HTTP 200; Consul local trả leader |
| API Gateway | `GET http://localhost:9000/health` | HTTP 200 |
| API Gateway | `GET http://localhost:9000/metrics` | HTTP 200; Prometheus metrics có phản hồi |
| Auth | `GET http://localhost:9001/health` | HTTP 200; DB connected |
| Catalog | `GET http://localhost:9002/health` | HTTP 200 |
| Inventory | `GET http://localhost:9003/health` | HTTP 200 |
| Purchase | `GET http://localhost:9004/health` | HTTP 200 |
| Sales | `GET http://localhost:9005/health` | HTTP 200 |
| Consul registry | Query health của từng service với `passing=1` | Mỗi service có một instance, check passing |

Các instance được đăng ký là `auth-1`, `catalog-1`, `inventory-1`, `purchase-1`, `sales-1`, địa chỉ `localhost` và cổng tương ứng 9001–9005. Đây chỉ chứng minh cấu hình local hiện tại; không chứng minh địa chỉ hoạt động trong Docker.

### Lỗi đã tái hiện

1. **Gateway làm mất HTTP status của service.** Gọi trực tiếp `GET /api/auth/me` không token trả HTTP 401. Gọi qua `GET /auth/api/auth/me` lại trả HTTP 200, body chứa thông tin 401 từ Auth và `X-Served-By: gateway-1`. Nguyên nhân: nhánh proxy chung luôn gọi upstream bằng GET và bọc body bằng `Results.ok(...)`; không giữ status upstream. Header `X-Served-By` đang chỉ định Gateway, không phải instance backend.
2. **Lỗi schema Catalog bị Gateway che thành HTTP 200.** Gọi một barcode giả qua Catalog trực tiếp trả HTTP 500; cùng request qua Gateway trả HTTP 200 với body `{"code":"INTERNAL_ERROR","message":"Catalog request could not be completed"}`. Stack trace ở Catalog ghi `SQLServerException: Invalid column name 'Concentration'`, phát sinh trong `DrugRepository.findByBarcode`. Nguyên nhân gốc là schema DB đang dùng thiếu cột mà truy vấn/model Catalog hiện tại cần.
3. **CORS trên prefix đang dùng không hoạt động.** Preflight `OPTIONS /auth/api/auth/login` trả HTTP 404. Cấu hình Gateway chỉ áp dụng CORS cho `pathPrefixes = ["/api"]`, trong khi route công khai hiện tại là `/auth`, `/catalog`, ...; preflight trên `/api/auth/login` được filter trả 200 nhưng đây không phải route login hiện có.
4. **Dừng instance chưa deregister ngay.** Sau khi dừng Purchase bằng cách kết thúc tiến trình ứng dụng, Consul vẫn giữ `purchase-1`; health check HTTP chuyển critical trong lần quan sát sau 14 giây. Kiểm tra sau đó cho thấy bản ghi đã được Consul dọn. Cấu hình có `DeregisterCriticalServiceAfter = 1m`, nhưng `onApplicationStop(...)` trong các `ConsulModule` chỉ là method thường, không được đăng ký làm lifecycle callback; vì vậy đường gọi `agentServiceDeregister` không được cài đặt khi ứng dụng khởi động. Thời điểm Consul dọn bản ghi không được đo chính xác.

## 2. Lỗi Gateway / Consul

Ưu tiên: **P0** = chặn bảo mật/nghiệp vụ; **P1** = chặn yêu cầu tích hợp/độ tin cậy; **P2** = cần chuẩn hóa/khả năng vận hành.

| ID | Ưu tiên | Lỗi / bằng chứng | Nguyên nhân gốc |
|---|---|---|---|
| GW-01 | P0 | Gateway không xác thực JWT, không kiểm tra role/branch; không có cấu hình JWT hoặc route-role ở Gateway. | `GatewayController` proxy trực tiếp; không có authentication/authorization filter. Auth tự xác thực một số endpoint, nhưng không đáp ứng yêu cầu bảo vệ tại Gateway và các service khác. |
| GW-02 | P0 | Gateway không forward `Authorization`, `X-User-Id`, `X-User-Role`, `X-Branch-Id` hay `X-Request-Id` xuống service. | Các nhánh proxy chỉ tạo WS request với URL/timeout/body; filter tạo request ID ở Gateway nhưng không đưa request ID vào request upstream. |
| GW-03 | P1 | Hầu hết route proxy bỏ method, body, query string và upstream status; lỗi upstream có thể thành HTTP 200. Đã tái hiện với Auth 401 và Catalog 500. | `proxyWithRetry` luôn gọi `.get()` và trả `Results.ok(body)`; chỉ Purchase có nhánh proxy riêng giữ method/body/query/status. |
| GW-04 | P1 | Không bảo đảm chỉ chọn instance healthy, không dùng round-robin thực tế và retry không loại instance lỗi. | `GatewayController` dùng `getCatalogService(...)` không lọc health rồi chọn `Random`; retry có thể chọn lại cùng instance. `ServiceDiscoveryClient` có `getHealthServices(..., true, ...)` và `RoundRobinLoadBalancer`, nhưng Gateway không inject/dùng hai lớp này. |
| GW-05 | P1 | Khi hết instance, response không đồng nhất; generic proxy trả body lỗi với HTTP 200. Không có envelope 503/502/504 chuẩn. | Nhánh generic trả `Results.ok(...)` cho cả “không tìm thấy service”, lỗi Consul và hết retry; Purchase có status 503/500 nhưng body riêng `{error:...}`. |
| GW-06 | P1 | Route không đúng prefix mục tiêu và không hoàn toàn cấu hình hóa. Gateway expose `/auth`, `/catalog`, `/purchase`, `/inventory`, `/sales`; mục tiêu yêu cầu `/api/...`. Còn có `/report` dù Report nằm ngoài phạm vi. | Play `conf/routes` khai báo route tĩnh; tên service được hard-code trong các method `proxy*` của controller. Không có file route/role config cho thêm service bằng cấu hình. |
| GW-07 | P1 | CORS áp dụng `/api`, nhưng các route Gateway hiện tại không dùng prefix đó; preflight trên route thật `/auth/...` trả 404. | `play.filters.cors.pathPrefixes` lệch với `conf/routes`. |
| GW-08 | P2 | Request ID sinh ở Gateway và được log/đưa vào response, nhưng không xuyên suốt service. | `RequestResponseLoggingFilter` chỉ sửa response; proxy không forward header request. |
| CS-01 | P1 | Đăng ký service dùng `localhost` làm địa chỉ và URL health check. | `service.host` mặc định `localhost` trong cấu hình mỗi service; Consul check được tạo từ chính địa chỉ đó. Hoạt động trong lần chạy cùng máy, nhưng trong container `localhost` trỏ về container Consul/service hiện tại, không phải container đích. |
| CS-02 | P1 | Deregister lúc shutdown không được nối vào Play lifecycle. Sau khi dừng Purchase, service còn trong registry ở trạng thái critical. | `onApplicationStop(...)` được khai báo như method trên Guice module, không phải callback được Play gọi; do đó shutdown hook chứa `agentServiceDeregister` không được cài. Health check có `DeregisterCriticalServiceAfter = 1m`, nên Consul chỉ có thể dọn trễ sau khi check critical. |
| CS-03 | P1 | ID mặc định là tên cố định như `auth-1`; nhiều instance có thể đăng ký trùng ID nếu không override. Cổng đăng ký lấy từ `PLAY_HTTP_PORT`, không tự suy ra từ cổng truyền cho `sbt run`. | Cấu hình `service.instance.id` và `service.port` độc lập với cổng runtime. Script demo có override ID cho instance 2 nhưng không đồng bộ đầy đủ địa chỉ/cổng đăng ký. |
| CS-04 | P2 | Có nhiều bản sao `ConsulModule` theo service; Gateway có module tương tự nhưng chỉ tạo client, không đăng ký service. | Không có helper đăng ký dùng chung; cấu hình mặc định Consul vẫn là `localhost:8500`. |

**Health check:** Trong lần chạy cục bộ, `GET /health` của Gateway và năm service trả 200; tất cả instance đăng ký đều `passing` khi được kiểm tra. Health của các service nghiệp vụ phụ thuộc DB; Gateway health chỉ phản ánh Gateway/JVM và không xác nhận Consul/service dependency. Đường health local pass không chứng minh được health check truy cập được trong Docker.

## 3. Bảng service, endpoint và lệch chuẩn

| Service | Port local | Endpoint / prefix hiện có | Quan sát và điểm lệch |
|---|---:|---|---|
| Auth | 9001 | `/api/auth/{login,refresh,logout,validate,me,current-branch}`, `/api/auth/profile`, `/api/roles`, `/api/permissions`, `/api/users`, `/api/audit-logs`, `/health` | Phát hành JWT có `sub`, `role`, `branch_ids`, `exp`, `jti`; thiếu issuer/audience. Dùng `Integer` cho user/branch IDs. Error handler trả `{status,code,message,path}`; một số action/controller có biến thể. |
| Catalog | 9002 | `/api/v1/catalog/branches`, `drugs`, `drug-groups`, `manufacturers`, `units`, `suppliers`, `customers`, `/health` | Prefix nội bộ có version và `catalog`; route Gateway là `/catalog/*` nên client hiện phải gọi dạng `/catalog/api/v1/catalog/...`. Model/DTO dùng các ID kiểu `Long`; JSON chủ yếu camelCase. Lỗi thường `{code,message}`. Pagination ví dụ `{items,page,size,total}`; `size` bị giới hạn 100. Live test phát hiện schema thiếu `Concentration`. |
| Inventory | 9003 | `/api/inventory/lots`, `/api/inventory/lots/:medicineId`, `/api/drug-lots`, `/api/v1/inventory/ledger`, `/api/v1/inventory/transfers`, `/health` | Nhiều nhóm prefix/version cùng tồn tại. `medicineId` cũ là `String`, còn `drugId`, `lotId` thường là `Long`, ledger có một số ID `Integer`. Pagination không thống nhất: DrugLot mặc định `page=0,size=20`; endpoint khác thường `page=1,size=20`. Error DTO `{code,message,details}`. |
| Purchase | 9004 | `/purchase-orders`, `/purchase-receipts`, `/supplier-returns`, `/supplier-payments`, `/supplier-debt/:supplierId`, `/health` | Gateway prefix là `/purchase/*`; endpoint nội bộ không có `/api`. Thành công thường `{success:true,data:...}`; lỗi `{success:false,error:...}`. Supplier/branch IDs dùng `Integer`, purchase entity IDs dùng `Long`; list không có pagination thống nhất. |
| Sales | 9005 | `/shifts`, `/invoice-details`, `/returns`, `/prescriptions`, `/invoices`, `/revenue`, `/health` | Nhiều comment mô tả `/api/v1/sales/...` nhưng routes thật không có prefix đó; qua Gateway phải ghép `/sales/...`. IDs phần lớn `Long`; response dao động giữa object/list và `{success,data,page,pageSize,total}`. Một số endpoint đặt `total` bằng số item của trang thay vì tổng toàn bộ bản ghi. |

### Lệch liên service / cấu hình dữ liệu

- Cả năm service mặc định trỏ cùng database `PharmacyManagementSOA`; chưa tách database/schema riêng.
- `Sales InventoryClient` hard-code `http://localhost:9004/api/v1/inventory` (9004 là Purchase trong bảng port hiện tại). Các endpoint `/stock-by-lot`, `/deduct`, `/restore-by-invoice` mà client gọi không xuất hiện trong routes hiện tại của Inventory (Inventory chạy 9003).
- Schema ID chưa thống nhất: Auth dùng `Integer`; Catalog chủ yếu `Long`; Inventory có `Long`, `Integer` và `String`; Purchase trộn `Integer`/`Long`; Sales chủ yếu `Long`.
- JSON field names phần lớn camelCase nhưng cùng ý nghĩa có tên khác nhau (ví dụ `medicineId` so với `drugId`); envelope lỗi/thành công và tên trường phân trang khác nhau (`size`/`pageSize`, `items`/`data`).
- Service-to-service auth chưa thống nhất: Auth tự validate JWT trên một số endpoint; Inventory có controller dùng role/header; Catalog và Purchase không có xác thực JWT dùng chung; Sales đọc một số header nhưng chưa có middleware thống nhất.
- JWT secret nằm riêng trong cấu hình Auth; Gateway và các service nghiệp vụ chưa validate token. Auth phát token thiếu `iss`/`aud`; Auth dev dùng secret mặc định và log cảnh báo khi chạy.

## 4. Còn thiếu so với yêu cầu tích hợp

- Route `/api/{auth,catalog,purchase,inventory,sales}` theo cấu hình; bảng route-role cấu hình hóa; chính sách giữ hay đổi prefix Catalog cần được chốt trước khi sửa.
- Helper Consul đăng ký chung + resolver healthy/round-robin/cache hoặc blocking query; địa chỉ container; unique instance ID/cổng; deregister thực sự khi shutdown.
- Gateway: JWT, role và branch check; forward identity/request headers; giữ method/body/query/status; 503/502/504 chuẩn; CORS đúng prefix; request ID xuyên service.
- Service: `/health` hiện có ở cả năm service nhưng đang gắn DB check và phải kiểm tra lại tiêu chí liveness/readiness. JWT defense-in-depth chưa thống nhất.
- Sales–Inventory và các luồng liên service chưa hoạt động thống nhất; URL/route Inventory đang lệch; chưa có resolver chung, retry/idempotency/compensation/FEFO đầy đủ.
- Đồng bộ API contract: IDs, JSON casing/naming, lỗi, pagination, ngày/giờ và status codes.
- Compose, `.env.example`, migration/seed có thể tái lập, Postman/smoke test và integration tests chưa sẵn có. Không có `docker-compose.yml`; Docker không khả dụng trong lần khảo sát này.
- Chưa có `docs/ROUTING.md`, `docs/ADD_NEW_SERVICE.md`, stub service chỉ cấu hình và báo cáo bàn giao cuối.
- Report Service hiện diện trong repo nhưng thuộc ngoài phạm vi; không đưa vào kế hoạch triển khai.

## 5. Kết luận cổng Giai đoạn 0

- [x] Đã khảo sát cấu trúc, công nghệ, port, route, auth và Consul.

> Bản audit này là snapshot trước Giai đoạn 1. Các thay đổi đã triển khai và những gate còn chờ xác minh được theo dõi trong [`CHANGELOG_INTEGRATION.md`](./CHANGELOG_INTEGRATION.md) và [`INTEGRATION_PLAN.md`](./INTEGRATION_PLAN.md).
- [x] Đã chạy Consul + Gateway + năm service trong môi trường local; kiểm tra health và trạng thái Consul.
- [x] Đã tái hiện lỗi HTTP status của Gateway, CORS prefix và Catalog DB schema.
- [x] Đã ghi các khác biệt API và yêu cầu còn thiếu.
- [x] Tại thời điểm hoàn tất Giai đoạn 0, chỉ tạo tài liệu và chờ xác nhận kế hoạch trước khi sửa mã nguồn.
