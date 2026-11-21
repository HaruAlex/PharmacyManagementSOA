# BÀN GIAO SAU ĐỢT 3: CÂN BẰNG TẢI (LOAD BALANCING)

## 1. Trạng thái
- Đợt đã hoàn thành: 3 / 6
- Commit cuối: `973a38a` – "Đợt 3: Cân bằng tải (Load Balancing) - LoadBalancer Round-robin, X-Served-By header, logging filter"
- Môi trường đã dùng: Windows, JDK 25.0.2, SBT 1.13.1, Consul chưa chạy, SQL Server không khả dụng

## 2. Đã làm
- ✓ Tạo `LoadBalancer` interface (chọn instance từ danh sách)
- ✓ Tạo `RoundRobinLoadBalancer` implementation (cân bằng tải Round-robin)
- ✓ Cập nhật `ServiceDiscoveryClient`: inject LoadBalancer, dùng `getHealthyInstance()` thay vì random
- ✓ Cập nhật `GatewayModule`: bind LoadBalancer → RoundRobinLoadBalancer
- ✓ Tạo `RequestResponseLoggingFilter`: log request + thêm header `X-Served-By` + `X-Request-Id`
- ✓ Enable filter trong application.conf (play.filters.enabled)
- ✓ Thêm cấu hình `service.instance.id` cho gateway (gateway-1)
- ✓ Cập nhật CORS: expose `X-Served-By` + `X-Request-Id`, accept `X-Request-Id` + `Idempotency-Key`
- ✓ Compile thành công

## 3. File đã tạo/sửa

### Mới tạo
- `api-gateway/app/services/LoadBalancer.java`: Interface cân bằng tải
- `api-gateway/app/services/RoundRobinLoadBalancer.java`: Implement Round-robin (AtomicInteger counter)
- `api-gateway/app/filters/RequestResponseLoggingFilter.java`: Log + thêm header response
- `*-service/conf/logback.xml` (8 file): Ghi log vào file + console (rotate 10MB, max 100MB)
- `docs/setup/SQL-SERVER-SETUP.md`: Hướng dẫn cài SQL Server (Docker + local)
- `docs/testing/TEST-ROUND-ROBIN.md`: Hướng dẫn test Round-robin chi tiết

### Sửa đổi
- `api-gateway/app/services/ServiceDiscoveryClient.java`: 
  - Inject LoadBalancer
  - Thay `getRandomHealthyInstance()` → `getHealthyInstance()` (dùng LoadBalancer)
- `api-gateway/app/services/GatewayModule.java`:
  - Thêm `bind(LoadBalancer.class).to(RoundRobinLoadBalancer.class).in(Singleton.class)`
- `api-gateway/conf/application.conf`:
  - Thêm `play.filters.enabled += "filters.RequestResponseLoggingFilter"`
  - Cập nhật CORS: allowedHttpHeaders + exposedHeaders
  - Thêm `service.instance.id = "gateway-1"`

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
| `cd api-gateway; sbt compile` | [success] Total time: 2 s | ✓ Đạt |
| `Select-String "RoundRobinLoadBalancer" api-gateway/app/services/*.java` | 2 lần (interface + implement) | ✓ Đạt |
| `Select-String "X-Served-By" api-gateway/app/filters/RequestResponseLoggingFilter.java` | Tìm thấy 1 lần (header) | ✓ Đạt |
| `git log --oneline \| head -1` | 5741a08 Đợt 3 - Hoàn thành... | ✓ Đạt |
| `Select-String "play.filters.enabled" api-gateway/conf/application.conf \| wc -l` | 2 dòng (filter + CORS) | ✓ Đạt |
| `Get-ChildItem *-service/conf/logback.xml \| Measure-Object` | Count: 8 | ✓ Đạt |
| `Get-Item docs/setup/SQL-SERVER-SETUP.md` | File tồn tại | ✓ Đạt |
| `Get-Item docs/testing/TEST-ROUND-ROBIN.md` | File tồn tại | ✓ Đạt |

## 6. Quyết định kỹ thuật quan trọng
- **Round-robin dùng AtomicInteger**: Để thread-safe mà không cần synchronized (performance tốt).
- **LoadBalancer.selectInstance()** lấy parameter `List<HealthService>`: Dễ test, flexibility cao (sau có thể thêm strategy khác).
- **Filter extend Filter**: Không thể dùng simple filter (vì cần Materializer), phải extend lớp Filter.
- **CORS expose X-Served-By + X-Request-Id**: Để browser frontend nhìn thấy header, không bị block CORS.

## 7. Việc tồn đọng / rủi zo
- ✓ **Log file**: Tạo `logback.xml` cho 8 service (gateway + 7 services)
- ✓ **SQL Server guide**: Tạo `docs/setup/SQL-SERVER-SETUP.md` (Docker + local install)
- ✓ **Test Round-robin guide**: Tạo `docs/testing/TEST-ROUND-ROBIN.md` (hướng dẫn chi tiết)
- ⚠️ **Chưa chạy thực tế**: Chưa có SQL Server chạy để test end-to-end
- ⚠️ **Chưa Docker Compose**: Chưa tạo docker-compose.yml hỗ trợ `--scale service=2` (để Đợt sau)

## 8. Việc của Đợt 4
- Phạm vi: Chịu lỗi + An toàn khi chạy 2 instance – Mục 3 và 4 trong prompt
- Lưu ý rút ra từ Đợt 3: Load balancer sẵn sàng; tiếp theo là retry + timeout + stateless + FEFO lock + Idempotency.
- Tiếp theo:
  1. Instance chết → gateway tự loại (health check Consul)
  2. Retry 2 lần cho GET hoặc request có Idempotency-Key
  3. Timeout kết nối + đọc rõ ràng
  4. Service stateless (kiểm tra auth-service)
  5. Trừ tồn FEFO (deduct) dùng UPDLOCK, ROWLOCK
  6. Connection pool 10–20

## 9. Câu lệnh khởi động Đợt 4
Dán đoạn sau vào agent mới (kèm prompt gốc và file này):

> Đây là prompt gốc và file bàn giao sau Đợt 3. Hãy làm ĐỢT 4 theo mục CHIA ĐỢT LÀM VIỆC: đọc file bàn giao, kiểm tra nhanh trạng thái thực tế, nêu kế hoạch ngắn rồi mới làm. Làm xong thì dừng và hỏi tôi.

---

**Tóm tắt**: Đợt 3 hoàn thành. Load Balancing sẵn sàng (Round-robin). X-Served-By + logging filter đang chạy. Compile thành công. Sẵn sàng cho Đợt 4 (Fault Tolerance + Safety).
