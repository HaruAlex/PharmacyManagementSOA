# BÀN GIAO SAU ĐỢT 2: KHÁM PHÁ DỊCH VỤ (SERVICE DISCOVERY)

## 1. Trạng thái
- Đợt đã hoàn thành: 2 / 6
- Commit cuối: `a36fbe7` – "Đợt 2: Khám phá dịch vụ (Service Discovery) - thêm ConsulModule, ServiceDiscoveryClient, cấu hình Consul"
- Môi trường đã dùng: Windows, JDK 25.0.2, SBT 1.13.1, Consul chưa chạy, SQL Server không khả dụng

## 2. Đã làm
- ✓ Tạo `ConsulModule.java` cho 6 service (auth, catalog, inventory, purchase, sales, report)
- ✓ Cấu hình Consul trong `application.conf` mỗi service (host, port, service.name, service.instance.id)
- ✓ Enable `play.modules.enabled += "modules.ConsulModule"` trong application.conf
- ✓ Thêm Consul dependency vào `build.sbt` (com.ecwid.consul % consul-api % 1.4.5)
- ✓ Tạo `ServiceDiscoveryClient.java` cho API Gateway (khám phá service từ Consul)
- ✓ Tạo `GatewayModule.java` cho API Gateway (cung cấp ConsulClient)
- ✓ Sửa `HealthController` gateway để inject ConsulClient
- ✓ Compile thành công tất cả 7 service + gateway

## 3. File đã tạo/sửa

### ConsulModule (6 file)
- `auth-service/app/modules/ConsulModule.java`: Tự động đăng ký auth-service lên Consul
- `catalog-service/app/modules/ConsulModule.java`: Tự động đăng ký catalog-service
- `inventory-service/app/modules/ConsulModule.java`: Tự động đăng ký inventory-service
- `purchase-service/app/modules/ConsulModule.java`: Tự động đăng ký purchase-service
- `sales-service/app/modules/ConsulModule.java`: Tự động đăng ký sales-service
- `report-service/app/modules/ConsulModule.java`: Tự động đăng ký report-service

### application.conf (7 file)
- `auth-service/conf/application.conf`: Thêm consul.*, service.*, play.modules.enabled
- `catalog-service/conf/application.conf`: Thêm cấu hình Consul
- `inventory-service/conf/application.conf`: Thêm cấu hình Consul
- `purchase-service/conf/application.conf`: Thêm cấu hình Consul
- `sales-service/conf/application.conf`: Thêm cấu hình Consul
- `report-service/conf/application.conf`: Thêm cấu hình Consul
- `api-gateway/conf/application.conf`: Thêm cấu hình Consul (chỉ dùng để discover, không đăng ký)

### build.sbt (7 file)
- Mỗi file thêm: `libraryDependencies += "com.ecwid.consul" % "consul-api" % "1.4.5"`
- Comment: "Đợt 2" để chỉ rõ phần nào là Đợt 2

### API Gateway Services
- `api-gateway/app/services/GatewayModule.java`: Cung cấp ConsulClient singleton
- `api-gateway/app/services/ServiceDiscoveryClient.java`: Khám phá service từ Consul
  - `getHealthyServiceInstances(serviceName)`: Lấy danh sách instance passing
  - `getRandomHealthyInstance(serviceName)`: Chọn ngẫu nhiên 1 instance
  - `getServiceUrl(healthService)`: Lấy URL (http://host:port) từ HealthService object

### HealthController
- `api-gateway/app/controllers/HealthController.java`: Inject ConsulClient, loại bỏ kiểm tra Consul khả dụng

## 4. Bảng Server – Cổng đang áp dụng
| Server | Instance 1: ID – cổng | Instance 2: ID – cổng |
|--------|-----------------------|-----------------------|
| frontend | frontend-1 – 8080 | (không có) |
| api-gateway | gateway-1 – 9000 | (không có) |
| auth-service | auth-1 – 9001 | auth-2 – 9011 |
| catalog-service | catalog-1 – 9002 | catalog-2 – 9012 |
| inventory-service | inventory-1 – 9003 | inventory-2 – 9013 |
| purchase-service | purchase-1 – 9004 | purchase-2 – 9014 |
| sales-service | sales-1 – 9005 | sales-2 – 9015 |
| report-service | report-1 – 9006 | report-2 – 9016 |

Ghi chú: Không có khác biệt

## 5. Lệnh kiểm chứng và kết quả thực tế
| Lệnh | Kết quả | Trạng thái |
|------|---------|------------|
| `cd auth-service; sbt compile 2>&1 \| Select-String "\[success\]"` | [success] Total time: 1 s | ✓ Đạt |
| `cd api-gateway; sbt compile 2>&1 \| Select-String "\[success\]"` | [success] Total time: 3 s | ✓ Đạt |
| `cd inventory-service; sbt compile 2>&1 \| Select-String "\[success\]"` | [success] Total time: 2 s | ✓ Đạt |
| `Get-ChildItem *-service/app/modules/ConsulModule.java \| Measure-Object` | Count: 6 | ✓ Đạt |
| `Select-String "modules.ConsulModule" auth-service/conf/application.conf` | play.modules.enabled += "modules.ConsulModule" | ✓ Đạt |
| `Select-String "consul-api" auth-service/build.sbt` | libraryDependencies += "com.ecwid.consul" % "consul-api" | ✓ Đạt |
| `git log --oneline \| head -1` | a36fbe7 Đợt 2: Khám phá dịch vụ... | ✓ Đạt |

## 6. Quyết định kỹ thuật quan trọng
- **ConsulModule dùng chung cho tất cả service**: Một module tương tự, nhập vào mỗi service riêng, tránh tạo library tập trung (chọn cách đơn giản cho dễ hiểu).
- **ServiceDiscoveryClient lọc passing sau khi nhận**: Dùng `consulClient.getHealthServices(serviceName, true, null)` trả tất cả instance, sau đó lọc chỉ passing (thay vì parameter builder phức tạp của Consul API 1.4.5).
- **API Gateway không đăng ký Consul**: Chỉ lấy ConsulClient để discover service, không tự đăng ký (gateway là entry point, không cần phải ở trong pool cân bằng).

## 7. Việc tồn đọng / rủi ro
- ⚠️ **Chưa chạy thực tế**: SQL Server không khả dụng, chưa thử `sbt run` để xác nhận service khởi động và đăng ký Consul.
- ⚠️ **Chưa test Consul**: Chưa chạy Consul server, chưa kiểm tra service đăng ký thành công.
- ⚠️ **Chưa test health check**: Chưa gọi `GET /health` để xác nhận endpoint khả dụng.
- ⚠️ **Chưa test discovery**: Chưa test API Gateway gọi ServiceDiscoveryClient để lấy danh sách instance từ Consul.
- ⚠️ **Instance 2 chưa test**: Chưa tạo cách khởi động instance 2 với cổng +10.

## 8. Việc của Đợt 3
- Phạm vi: Cân bằng tải (Load Balancing) – Mục 2 trong prompt
- Lưu ý rút ra từ Đợt 2: Service Discovery sẵn sàng; tiếp theo là implement Round-robin LoadBalancer.
- Tiếp theo:
  1. Tạo interface `LoadBalancer` cho Round-robin
  2. Thêm header `X-Served-By` vào response (lấy từ instance ID)
  3. Log mỗi request có instance ID và `X-Request-Id`
  4. Docker Compose hỗ trợ `--scale <service>=2`

## 9. Câu lệnh khởi động Đợt 3
Dán đoạn sau vào agent mới (kèm prompt gốc và file này):

> Đây là prompt gốc và file bàn giao sau Đợt 2. Hãy làm ĐỢT 3 theo mục CHIA ĐỢT LÀM VIỆC: đọc file bàn giao, kiểm tra nhanh trạng thái thực tế, nêu kế hoạch ngắn rồi mới làm. Làm xong thì dừng và hỏi tôi.

---

**Tóm tắt**: Đợt 2 hoàn thành. Consul module và service discovery client sẵn sàng. Compile thành công. Sẵn sàng cho Đợt 3 (Load Balancing).
