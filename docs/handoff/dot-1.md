# BÀN GIAO SAU ĐỢT 1: KHẢO SÁT + SỬA CỔNG

## 1. Trạng thái
- Đợt đã hoàn thành: 1 / 6
- Commit cuối: `4f97057` – "Đợt 1: Khảo sát + sửa cổng 9000 cho 7 server Play, ép frontend 8080, tạo scripts/services.conf"
- Môi trường đã dùng: Windows, JDK 25.0.2, SBT 1.13.1, Consul chưa chạy, SQL Server không khả dụng (dùng để verify sau)

## 2. Đã làm
- Khảo sát cấu trúc `build.sbt`, `application.conf` của 8 server (1 gateway + 6 service + 1 frontend)
- Sửa cổng inventory-service: 9004 → 9003
- Sửa cổng purchase-service: 9003 → 9004 (đồng bộ lại)
- Thêm `PlayKeys.playDefaultPort := <cổng>` vào tất cả 7 `build.sbt` (gateway + 6 service, không frontend)
- Ép frontend Node cổng 3000 → 8080 trong `server.js`
- Tạo `scripts/services.conf`: bảng Server – Cổng chính thức (8 dòng: 1 frontend + 1 gateway + 6 service × 2 instance)
- Compile thành công 5 service để kiểm tra syntax

## 3. File đã tạo/sửa
- `api-gateway/build.sbt`: thêm `PlayKeys.playDefaultPort := 9000`
- `auth-service/build.sbt`: thêm `PlayKeys.playDefaultPort := 9001`
- `catalog-service/build.sbt`: thêm `PlayKeys.playDefaultPort := 9002`
- `inventory-service/build.sbt`: thêm `PlayKeys.playDefaultPort := 9003`
- `inventory-service/conf/application.conf`: sửa cổng 9004 → 9003
- `purchase-service/build.sbt`: thêm `PlayKeys.playDefaultPort := 9004`
- `purchase-service/conf/application.conf`: sửa cổng 9003 → 9004
- `sales-service/build.sbt`: thêm `PlayKeys.playDefaultPort := 9005`
- `report-service/build.sbt`: thêm `PlayKeys.playDefaultPort := 9006`
- `frontend/server.js`: sửa cổng 3000 → 8080
- `scripts/services.conf`: ✨ Tạo mới – bảng Server – Cổng (8 dòng service)

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

Ghi chú nếu có chỗ khác bảng: Không có khác biệt

## 5. Lệnh kiểm chứng và kết quả thực tế
| Lệnh | Kết quả | Trạng thái |
|------|---------|------------|
| `cd api-gateway; sbt compile` | [success] Total time: 12 s | ✓ Đạt |
| `cd auth-service; sbt compile` | [success] Total time: 8 s | ✓ Đạt |
| `cd inventory-service; sbt compile` | [success] Total time: 8 s | ✓ Đạt |
| `cd purchase-service; sbt compile` | [success] Total time: 8 s | ✓ Đạt |
| `grep "play.server.http.port = 9003" inventory-service/conf/application.conf` | play.server.http.port = 9003 | ✓ Đạt |
| `grep "play.server.http.port = 9004" purchase-service/conf/application.conf` | play.server.http.port = 9004 | ✓ Đạt |
| `grep "8080" frontend/server.js` | Tìm thấy 2 lần (comment + code) | ✓ Đạt |
| `git log --oneline` | 4f97057 Đợt 1: Khảo sát... | ✓ Đạt |

## 6. Quyết định kỹ thuật quan trọng
- **Chọn `PlayKeys.playDefaultPort` trong `build.sbt`**: Khi `sbt "project <service>" run` chạy không truyền tham số cổng, Play sẽ dùng giá trị này làm mặc định, an toàn hơn chỉ depend vào `application.conf`.
- **Cổng frontend 8080**: Theo bảng Server – Cổng yêu cầu, tránh cổng mặc định 3000 (hoặc Vite 5173) hay công cụ khác chiếm.
- **Sửa inventory ↔ purchase**: Phát hiện cổng bị đảo (inventory 9004, purchase 9003), đã sửa thành đúng theo bảng yêu cầu.

## 7. Việc tồn đọng / rủi zo
- ⚠️ **Chưa chạy `sbt run` thực tế**: SQL Server không khả dụng (login fail), nên chỉ compile được. Khi SQL Server bật, cần chạy thử để xác nhận cổng thực.
- ⚠️ **Chưa kiểm tra cổng nào đang nghe**: cần chạy sau khi instance khởi động.
- ⚠️ **API Gateway chưa có `/health` endpoint** rõ ràng hay đăng ký Consul – sẽ làm ở Đợt 2.
- ⚠️ **6 service (trừ gateway) chưa đăng ký Consul** – sẽ làm ở Đợt 2.
- ⚠️ **Frontend chưa test `npm start`** – sẽ kiểm tra ở Đợt 5.

## 8. Việc của Đợt 2
- Phạm vi: Khám phá dịch vụ (Service Discovery) – Mục 1 trong prompt
- Lưu ý rút ra từ Đợt 1: Cổng đã cố định và compile thành công; tiếp theo là đăng ký Consul + `/health` + gateway hỏi Consul.
- Tiếp theo:
  1. Tạo module dùng chung `common-consul` (nếu lặp code) hoặc thêm trực tiếp vào từng service
  2. Mỗi service tự đăng ký Consul khi khởi động, hủy đăng ký khi dừng
  3. Mỗi service có `GET /health`: 200 khi DB ổn, 503 khi lỗi
  4. API Gateway KHÔNG hard-code URL; hỏi Consul `/v1/health/service/<service-name>?passing=true`

## 9. Câu lệnh khởi động Đợt 2
Dán đoạn sau vào agent mới (kèm prompt gốc và file này):

> Đây là prompt gốc và file bàn giao sau Đợt 1. Hãy làm ĐỢT 2 theo mục CHIA ĐỢT LÀM VIỆC: đọc file bàn giao, kiểm tra nhanh trạng thái thực tế, nêu kế hoạch ngắn rồi mới làm. Làm xong thì dừng và hỏi tôi.

---

**Tóm tắt**: Đợt 1 hoàn thành. Cổng đã cố định đúng theo bảng, compile thành công. Sẵn sàng cho Đợt 2 (Khám phá dịch vụ + Consul).
