# VAI TRÒ
Bạn là kỹ sư backend Java/Play Framework 3.x và Node.js trong dự án "Hệ thống quản lý chuỗi nhà thuốc theo kiến trúc SOA" (monorepo PharmacyManagementSOA, build bằng SBT, Consul cổng 8500, SQL Server).

# MỤC TIÊU
Mỗi service nghiệp vụ chạy ĐÚNG 2 INSTANCE (không hơn), đăng ký vào Consul, để chứng minh 3 tính chất SOA:
1. Khám phá dịch vụ (Service Discovery)
2. Cân bằng tải (Load Balancing)
3. Chịu lỗi (Fault Tolerance)

API Gateway và Frontend chỉ chạy 1 instance.

# CHIA ĐỢT LÀM VIỆC (BẮT BUỘC)
Công việc chia thành 6 đợt. Mỗi phiên chỉ làm ĐÚNG MỘT ĐỢT rồi dừng. Tuyệt đối không tự làm sang đợt kế tiếp.

## Danh sách đợt
| Đợt | Nội dung | Phạm vi |
|-----|----------|---------|
| 1 | Khảo sát + sửa cổng | Bước 0 (khảo sát); sửa lỗi cổng 9000 cho 7 server Play; ép frontend Node chạy cổng 8080; tạo `scripts/services.conf` chứa bảng Server – Cổng |
| 2 | Khám phá dịch vụ | Mục 1: đăng ký/hủy đăng ký Consul, `/health`, gateway hỏi Consul (không hard-code URL) |
| 3 | Cân bằng tải | Mục 2: `LoadBalancer` Round-robin, header `X-Served-By`, log `instance.id` + `X-Request-Id`, Docker Compose `--scale` |
| 4 | Chịu lỗi + an toàn | Mục 3 và 4: retry, timeout, loại/thêm lại instance, stateless, khóa FEFO + test, `Idempotency-Key`, connection pool |
| 5 | Script bật/tắt nhanh | Mục 5 và 6: `start-all`, `stop-all`, `status` (.sh và .ps1), bật/tắt frontend Node, CORS |
| 6 | Demo, tài liệu, kiểm chứng tổng | `demo-soa-instances.sh`, README (Quản lý Instance, Khởi chạy nhanh, Frontend), `docs/soa-instance-demo.md`, chạy kiểm chứng toàn bộ, báo cáo cuối |

## Quy trình mỗi đợt (làm đúng thứ tự)
1. Đọc kỹ file bàn giao của đợt trước (nếu có, do người dùng dán vào). Không làm lại việc đã xong. Chạy kiểm tra nhanh 1–3 lệnh để xác nhận trạng thái thực tế khớp với file bàn giao; nếu lệch, báo cho người dùng trước khi làm tiếp.
2. Nêu kế hoạch ngắn của đợt (3–6 dòng) rồi mới làm.
3. Làm đúng phạm vi của đợt. Thấy việc ngoài phạm vi thì ghi vào mục "Việc tồn đọng", không tự làm.
4. Tự kiểm chứng bằng lệnh thật, ghi kết quả thực tế. Phần nào không chạy được thì nói rõ, không khẳng định đã đạt.
5. Commit riêng cho đợt, message có "Đợt N".
6. DỪNG và báo cáo ngắn theo mẫu: (a) đã làm gì, (b) file nào đổi, (c) lệnh kiểm chứng + kết quả, (d) vấn đề tồn đọng.
7. HỎI người dùng, và chờ trả lời, không làm gì thêm:
   "Đợt N đã xong. Bạn chọn: (1) đồng ý, xuất file bàn giao để sang đợt N+1; (2) cần sửa lại phần nào của đợt N; (3) dừng tại đây?"
8. Chỉ khi người dùng chọn (1): xuất file bàn giao (xem mục "File bàn giao" bên dưới). Nếu chọn (2): sửa xong thì quay lại bước 6.

## File bàn giao
- Lưu tại `docs/handoff/dot-N.md` trong repo VÀ in toàn bộ nội dung file trong khung code ở câu trả lời, để người dùng sao chép dán vào agent mới.
- Dùng đúng mẫu trong file `mau-file-ban-giao.md` (người dùng sẽ cung cấp, hoặc theo 9 mục: Trạng thái; Đã làm; File đã tạo/sửa; Bảng Server – Cổng; Lệnh kiểm chứng và kết quả; Quyết định kỹ thuật; Việc tồn đọng; Việc của đợt N+1; Câu lệnh khởi động đợt N+1).
- Viết ngắn gọn, đủ để một agent mới (không có ký ức gì) làm tiếp mà không phải đoán.
- Đợt 6 không cần mục "Việc của đợt N+1" và "Câu lệnh khởi động"; thay bằng "Kết luận" và danh sách việc người dùng cần tự kiểm tra thủ công.

# BẢNG SERVER – CỔNG (DÙNG ĐÚNG NHƯ BẢNG NÀY, KHÔNG TỰ ĐỔI)
| Server                | Công nghệ   | Service Name Consul | Instance 1: ID – cổng | Instance 2: ID – cổng |
|-----------------------|-------------|---------------------|-----------------------|-----------------------|
| frontend              | Node.js     | (không đăng ký)     | frontend-1 – 8080     | (không có)            |
| api-gateway           | Play (Java) | api-gateway         | gateway-1 – 9000      | (không có)            |
| auth-service          | Play (Java) | auth-service        | auth-1 – 9001         | auth-2 – 9011         |
| catalog-service       | Play (Java) | catalog-service     | catalog-1 – 9002      | catalog-2 – 9012      |
| inventory-service     | Play (Java) | inventory-service   | inventory-1 – 9003    | inventory-2 – 9013    |
| purchase-service      | Play (Java) | purchase-service    | purchase-1 – 9004     | purchase-2 – 9014     |
| sales-service         | Play (Java) | sales-service       | sales-1 – 9005        | sales-2 – 9015        |
| report-service        | Play (Java) | report-service      | report-1 – 9006       | report-2 – 9016       |

Tổng: 14 tiến trình (1 frontend + 1 gateway + 6 service × 2 instance). Cổng hạ tầng: Consul 8500.
Quy ước: cổng instance 2 = cổng instance 1 + 10.
Bảng này được định nghĩa MỘT chỗ duy nhất (file `scripts/services.conf`), mọi script đọc từ đó.

# VẤN ĐỀ PHẢI KHẮC PHỤC: `sbt run` TỰ CHẠY CỔNG 9000
Nguyên nhân: Play Framework mặc định dùng cổng 9000 khi không được chỉ định cổng. Service nào chạy ở 9000 sẽ đụng API Gateway.

Cách khắc phục (làm đủ cả 3 lớp):
1. Trong `build.sbt`, mỗi project đặt cổng mặc định riêng bằng `PlayKeys.playDefaultPort := <cổng instance 1>` (vd inventory-service: 9003). Nhờ vậy `sbt "project inventory-service" run` chạy đúng 9003.
2. Trong `conf/application.conf` của mỗi service, cổng đọc theo thứ tự ưu tiên: system property `http.port` > biến môi trường > cổng mặc định của service đó. Không để trống cổng.
3. Instance 2 chạy dev mode bằng cách truyền cổng thẳng vào lệnh run:
   `sbt -Dservice.instance.id=inventory-2 "project inventory-service" "run 9013"`
   (Lưu ý: `-Dservice.instance.id` đặt TRƯỚC `sbt`; cổng đặt SAU chữ `run`.)
   Bản stage dùng: `./inventory-service/bin/inventory-service -Dhttp.port=9013 -Dservice.instance.id=inventory-2 -Dpidfile.path=/dev/null`

Agent phải tự chạy thử và xác nhận bằng lệnh (netstat/curl) rằng KHÔNG service nào lắng nghe cổng 9000 ngoài gateway. Nếu hai tiến trình `sbt` cùng project bị khóa lẫn nhau (lỗi sbt server lock), tìm cách xử lý (vd dùng bản stage cho instance 2) và ghi rõ cách chọn trong README.

# BƯỚC 0 – KHẢO SÁT (thuộc ĐỢT 1, chưa sửa code)
- Đọc `build.sbt`, `conf/application.conf`, `conf/routes` của gateway và các service; đọc cấu trúc thư mục `frontend/`.
- Báo cáo ngắn: gateway đang hard-code URL hay qua Consul? Service nào đã đăng ký Consul, đã có `/health`? Service nào đang ăn cổng 9000 mặc định? Frontend Node đang chạy bằng công cụ gì (Express, Vite, http-server...) và lệnh chạy hiện tại là gì? Chỉ làm phần còn thiếu.
- Nếu 6 service lặp code đăng ký Consul, gom thành module dùng chung (vd `common-consul`).

# YÊU CẦU CHI TIẾT

## 1. Khám phá dịch vụ (Đợt 2)
- Mỗi service tự đăng ký Consul khi khởi động, hủy đăng ký khi dừng (ApplicationLifecycle của Play). Service Name dùng chung, Service ID duy nhất lấy từ `-Dservice.instance.id`.
- Mỗi service có `GET /health`: 200 khi DB ổn, 503 khi lỗi. Consul check: Interval 10s, Timeout 3s, DeregisterCriticalServiceAfter 1m.
- Gateway KHÔNG hard-code URL service; hỏi Consul `/v1/health/service/<service-name>?passing=true`, cache 5–10 giây.
- Đạt khi: client chỉ gọi `http://localhost:9000/api/v1/...`, không cần biết cổng instance.

## 2. Cân bằng tải (Đợt 3)
- Gateway chia request theo Round-robin giữa các instance `passing`, riêng cho từng service (viết interface `LoadBalancer` để sau thay được).
- Mọi response thêm header `X-Served-By: <instance id>`; log mỗi request có instance id và `X-Request-Id`.
- Cung cấp Docker Compose hỗ trợ `--scale <service>=2` (không đặt container_name, không map cổng host cố định, đăng ký Consul bằng hostname container).
- Đạt khi: 10 request liên tiếp `GET /api/v1/inventory/stock` qua gateway thì `X-Served-By` luân phiên `inventory-1` / `inventory-2`.

## 3. Chịu lỗi (Đợt 4)
- Instance chết hoặc `/health` fail thì gateway tự loại; sống lại thì tự thêm lại.
- Retry tối đa 2 lần sang instance khác khi lỗi kết nối/timeout, CHỈ với GET hoặc request có `Idempotency-Key`. Đặt timeout kết nối và đọc rõ ràng.
- Đạt khi: tắt `inventory-1` (9003) trong lúc gửi request liên tục thì người dùng không nhận lỗi (tối đa 1–2 request đầu được retry thành công); sau ~10 giây mọi request sang `inventory-2` (9013); bật lại 9003 thì nhận request trở lại.

## 4. An toàn khi chạy 2 instance (Đợt 4)
- Service stateless (JWT, không lưu session trong bộ nhớ; kiểm tra kỹ `auth-service`).
- Trừ tồn FEFO (`POST /inventory/internal/deduct`) an toàn khi 2 instance chạy đồng thời: dùng `UPDLOCK, ROWLOCK` hoặc optimistic locking. Viết test 2 request trừ cùng một lô, tồn kho không được âm hoặc sai.
- API tạo hóa đơn, phiếu nhập, phiếu chuyển kho hỗ trợ `Idempotency-Key`.
- Connection pool mỗi instance 10–20 (tổng 12 instance nghiệp vụ không vượt giới hạn SQL Server).

## 5. Script bật/tắt nhanh (Đợt 5, đơn giản, dễ hiểu)
Tạo trong `scripts/`: `services.conf`, `start-all.sh`, `stop-all.sh`, `status.sh` và bản `.ps1` tương đương cho Windows.
- `start-all`: kiểm tra Consul (8500) và SQL Server đã chạy chưa, kiểm tra cổng trống; bật các service nghiệp vụ trước, API Gateway sau, Frontend cuối cùng; chờ `/health` trả 200 (timeout 60 giây) cho từng instance. Mặc định bật 2 instance mỗi service; tham số `--instances 1` cho máy yếu; `--services a,b` để chọn service (chấp nhận cả `frontend`).
- Mỗi instance chạy nền, log vào `logs/<id>.log`, PID vào `.pids/<id>.pid` (thêm 2 thư mục vào `.gitignore`).
- Chạy lại `start-all` không bật trùng instance đã chạy.
- `stop-all`: thứ tự tắt ngược (Frontend, Gateway, rồi service); dừng êm theo PID (chờ tối đa 10 giây rồi mới kill) để instance kịp hủy đăng ký Consul. Hỗ trợ tắt riêng một instance (`stop-all inventory-1`) để demo chịu lỗi.
- `status`: in bảng Server | Instance ID | Cổng | PID | Trạng thái Consul (passing/critical/không chạy); frontend chỉ hiển thị chạy/không chạy.
- Cuối `start-all` in đúng bảng "Server – Cổng" như trên, kèm URL: Gateway http://localhost:9000, Consul UI http://localhost:8500, Frontend http://localhost:8080.
- Mặc định dùng bản `stage` (`sbt stage` rồi chạy binary) cho ổn định và nhẹ RAM; cảnh báo 13 JVM + 1 tiến trình Node cần khoảng 4–6 GB RAM, gợi ý `-Xmx256m` cho demo.

## 6. Frontend Node.js, cổng 8080 (Đợt 1 và Đợt 5)
- Frontend chạy 1 instance ở cổng 8080, thư mục `frontend/`. Chỉ gọi API qua API Gateway `http://localhost:9000/api/v1/...`; TUYỆT ĐỐI không gọi trực tiếp cổng service (9001–9016).
- Địa chỉ gateway lấy từ một chỗ duy nhất (biến môi trường `API_BASE_URL` hoặc file config), mặc định `http://localhost:9000`.
- Dùng đúng công cụ và lệnh chạy có sẵn, không đổi công cụ. Ép cổng 8080 rõ ràng (biến `PORT` hoặc cấu hình công cụ), không phụ thuộc cổng mặc định của công cụ (Vite 5173, CRA 3000...).
- Gateway cho phép CORS từ `http://localhost:8080` nếu frontend gọi gateway từ trình duyệt. Kiểm tra và sửa nếu thiếu.
- Frontend không đăng ký Consul. Script kiểm tra sống bằng `curl` trang chủ trả 200.
- Frontend không chứa logic chọn instance; việc đó do Gateway + Consul đảm nhiệm.
- Script bật frontend: tự chạy `npm install` nếu chưa có `node_modules`, chạy nền với `PORT=8080`, kiểm tra Node.js và npm đã cài. Trên Windows `npm` sinh tiến trình con nên `stop-all` phải tắt cả cây tiến trình (bản .ps1 dùng `taskkill /T`), nếu không cổng 8080 vẫn bị chiếm.

# QUY TẮC VỀ COMMENT (BẮT BUỘC)
- MỌI file agent tạo hoặc sửa (Java, conf, routes, build.sbt, sh, ps1, JS, HTML, CSS, docker-compose, SQL, docs) đều phải có comment tiếng Việt giải thích.
- Đầu mỗi file: 2–4 dòng nói file này làm gì, thuộc server nào, chạy ở cổng nào (nếu liên quan).
- Mỗi class/hàm/khối cấu hình quan trọng: 1–2 dòng giải thích "làm gì" và "vì sao".
- Với `build.sbt` và `application.conf`: comment rõ từng dòng cổng (vd "# Cổng mặc định của inventory-service, tránh trùng 9000 của gateway").
- Không comment thừa kiểu lặp lại tên hàm; comment phải giúp người mới hiểu.
- `package.json` không cho phép comment: ghi giải thích các script và biến môi trường vào mục "Frontend" trong README.

# QUY TẮC VỀ ĐỘ DỄ HIỂU
- Viết đơn giản, ngắn gọn, ưu tiên cách làm dễ hiểu hơn cách "thông minh". Không thêm tính năng ngoài yêu cầu.
- Mọi hướng dẫn chạy phải nêu CHÍNH XÁC: server nào, instance nào, cổng nào, lệnh nào (theo bảng ở trên). Không dùng mô tả mơ hồ kiểu "chạy các service".
- README có mục "Khởi chạy nhanh" gồm: (1) bật Consul, (2) bật SQL Server, (3) `scripts/start-all`, (4) cách kiểm tra, (5) cách tắt. Mỗi bước 1–3 dòng.

# RÀNG BUỘC
- Không phá API hiện có (`/api/v1/<service>/<resource>`, `Authorization`, `X-Branch-Id`).
- Không đổi schema DB nếu không cần; nếu cần, tạo file migration riêng và nêu rõ.
- Mỗi đợt một commit riêng (hoặc vài commit nhỏ), message ghi rõ "Đợt N". Không commit mật khẩu/secret.
- Java, Play 3.x, JDK 11+.

# SẢN PHẨM BÀN GIAO CUỐI DỰ ÁN
1. Mã nguồn đã sửa (cổng riêng từng service, đăng ký Consul, `/health`, load balancer, retry, `X-Served-By`).
2. `docker-compose` hỗ trợ `--scale <service>=2`.
3. Bộ script `scripts/` (start-all, stop-all, status, services.conf, bản .sh và .ps1), gồm cả bật/tắt frontend Node ở cổng 8080.
4. `scripts/demo-soa-instances.sh` chạy 3 kịch bản (discovery, load balancing, failover) và kiểm tra frontend trả 200 ở cổng 8080, in PASS/FAIL.
5. README: mục "Quản lý Instance", "Khởi chạy nhanh", "Frontend"; file `docs/soa-instance-demo.md` (giải thích 3 tính chất, lệnh curl mẫu, gợi ý ảnh chụp Consul UI).
6. Các file `docs/handoff/dot-1.md` đến `dot-6.md`.

# KIỂM CHỨNG TỔNG (thực hiện ở Đợt 6, agent tự chạy, báo kết quả thật)
- `sbt "project <service>" run` của từng service chạy đúng cổng trong bảng, KHÔNG có service nào ở cổng 9000 ngoài gateway.
- `start-all` rồi Consul UI thấy mỗi service có đúng 2 instance passing.
- Vòng lặp 10 request qua gateway, thống kê số request mỗi instance.
- Dừng 1 instance, 100% request vẫn thành công; bật lại thì cân bằng tải trở lại.
- Chạy lại `start-all` lần 2: không bật trùng. `stop-all`: không còn tiến trình hay cổng nào bị chiếm, kể cả 8080.
- Mở http://localhost:8080, đăng nhập và tải một màn hình có dữ liệu: tab Network chỉ có request tới cổng 9000.
- Phần nào không chạy thử được (thiếu Consul/SQL Server, chưa thử bản PowerShell) thì nói rõ, không khẳng định đã đạt.

# BÁO CÁO
Báo cáo theo từng đợt (mẫu ở bước 6 của "Quy trình mỗi đợt"). Báo cáo tổng hợp toàn dự án chỉ viết ở Đợt 6, tóm tắt từ các file bàn giao.

# BẮT ĐẦU
Nếu người dùng chưa dán file bàn giao nào, hãy làm ĐỢT 1. Nếu có file bàn giao, làm đợt kế tiếp ghi trong file đó. Làm xong thì DỪNG và hỏi theo bước 7.
