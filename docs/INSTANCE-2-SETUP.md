# Hướng Dẫn Cấu Hình & Chạy Instance 2

**Đối tượng:** Nhà phát triển, DevOps  
**Mục tiêu:** Chạy 2 instance (Instance 1 + Instance 2) của mỗi service để demo Round-robin Load Balancing  
**Cổng:** Instance 2 = Instance 1 + 10  

---

## 📋 Bảng Instance 2 - Port

| Service | Instance 1 | Instance 2 | Consul Name |
|---------|-----------|-----------|-------------|
| auth-service | 9001 | **9011** | auth-service |
| catalog-service | 9002 | **9012** | catalog-service |
| inventory-service | 9003 | **9013** | inventory-service |
| purchase-service | 9004 | **9014** | purchase-service |
| sales-service | 9005 | **9015** | sales-service |
| report-service | 9006 | **9016** | report-service |

---

## 🚀 Cách Chạy Instance 2

### Lựa Chọn 1: Chạy Dev Mode (sbt run với port)

**Cách 1a: Truyền port trực tiếp vào lệnh run**

```bash
cd d:\Java\PharmacyManagementSOA\auth-service

# Instance 1 (cổng 9001)
sbt "run 9001"

# Instance 2 (cổng 9011) - Terminal khác
sbt -Dservice.instance.id=auth-2 "run 9011"
```

**Lưu ý:**
- Đặt `-Dservice.instance.id` **TRƯỚC** chữ `sbt`
- Port đặt **SAU** chữ `run`
- Hai terminal riêng biệt (SBT server sẽ lock nếu cùng terminal)

**Ví dụ đầy đủ với inventory-service:**

```bash
# Terminal 1
cd inventory-service
sbt "run 9003"

# Terminal 2 (chạy song song)
cd inventory-service
sbt -Dservice.instance.id=inventory-2 "run 9013"

# Mở Consul UI: http://localhost:8500/ui/
# Kiểm tra: inventory-service sẽ có 2 instance (9003 + 9013)
```

---

### Lựa Chọn 2: Dùng Bản Stage (Ổn Định Hơn)

Tạo bản stage một lần, rồi chạy binary với port khác nhau:

```bash
# Bước 1: Build stage (chỉ làm 1 lần)
cd inventory-service
sbt stage

# Bước 2: Chạy Instance 1 (dev mode)
sbt "run 9003"

# Bước 3: Chạy Instance 2 (binary + port)
# Windows
.\inventory-service\bin\inventory-service.bat -Dhttp.port=9013 -Dservice.instance.id=inventory-2 -Dpidfile.path=/dev/null

# Linux / macOS
./inventory-service/bin/inventory-service -Dhttp.port=9013 -Dservice.instance.id=inventory-2 -Dpidfile.path=/dev/null
```

**Ưu điểm:**
- Không bị kẹt SBT server lock
- Binary chạy nhanh
- Dễ tự động hóa bằng script

---

## 🔧 Cấu Hình Tự Động (application.conf)

Mỗi service phải cấu hình theo ưu tiên này:

```conf
# conf/application.conf

# ============================================================================
# PORT CONFIGURATION (ưu tiên từ cao xuống thấp)
# ============================================================================
# 1. System property (-Dhttp.port=XXXX)
# 2. Environment variable (HTTP_PORT)
# 3. Cổng mặc định service

# Ví dụ cho inventory-service:
play.server.provider = play.core.server.NettyServerProvider

# Cấu hình cổng (Play sẽ đọc từ system property trước)
http.port = ${?HTTP_PORT}
http.port = 9003  # Cổng mặc định (được override bởi system property)

# Service name cho Consul
service.name = inventory-service

# Instance ID (có thể override bằng -Dservice.instance.id)
service.instance.id = inventory-1
```

**Kiểm chứng:**

```bash
# Terminal 1: Chạy Instance 1 (cổng 9003 default)
cd inventory-service
sbt "run 9003"

# Terminal 2: Chạy Instance 2 (port 9013 override)
cd inventory-service
sbt -Dservice.instance.id=inventory-2 "run 9013"

# Terminal 3: Kiểm tra
curl http://localhost:9003/health      # Instance 1
curl http://localhost:9013/health      # Instance 2
```

---

## 📡 Xác Minh Đăng Ký Consul

Sau khi chạy Instance 1 + 2, kiểm tra Consul:

### Cách 1: Consul CLI

```bash
# Kiểm tra service
consul catalog services

# Chi tiết service inventory-service
consul catalog service inventory-service

# Output ví dụ:
# Node         Address         ServicePort ID
# inventory-1  127.0.0.1       9003        inventory-1
# inventory-2  127.0.0.1       9013        inventory-2
```

### Cách 2: Consul HTTP API

```bash
# Lấy tất cả instance của inventory-service
curl http://localhost:8500/v1/catalog/service/inventory-service | jq .

# Hoặc chỉ lấy passing (sống)
curl http://localhost:8500/v1/health/service/inventory-service?passing | jq .
```

### Cách 3: Consul UI

- Mở http://localhost:8500/ui/
- Click "Services" → "inventory-service"
- Sẽ thấy 2 instance: inventory-1 (9003), inventory-2 (9013)

---

## 🧪 Test Round-robin Load Balancing

Sau khi cả 2 instance chạy, test:

```bash
# Gửi 10 request qua gateway
for i in {1..10}; do
  curl -s -w "Request $i: X-Served-By=%{header{X-Served-By}}\n" \
    http://localhost:9000/health
done

# Kỳ vọng: X-Served-By sẽ xen kẽ giữa inventory-1 và inventory-2
# (hoặc gateway-1, nếu gateway xử lý trước)
```

**Output ví dụ:**
```
Request 1: X-Served-By=gateway-1
Request 2: X-Served-By=gateway-1
Request 3: X-Served-By=gateway-1
...
```

---

## 🔴 Lỗi Thường Gặp & Cách Khắc Phục

### Lỗi 1: Port 9011 đang sử dụng
```
Address already in use
```
**Giải pháp:**
- Kiểm tra tiến trình: `netstat -ano | findstr 9011` (Windows) hoặc `lsof -i :9011` (Mac/Linux)
- Kill tiến trình cũ rồi chạy lại

### Lỗi 2: SBT server lock
```
[error] The target ... is already being compiled
```
**Giải pháp:**
- Sử dụng terminal khác nhau cho mỗi instance
- Hoặc dùng bản stage thay vì `sbt run`
- Hoặc kill sbt server: `sbt -Dsbt.server.forcestart=true`

### Lỗi 3: Instance 2 không đăng ký Consul
```
Consul: 0 instance, expected 2
```
**Kiểm tra:**
```bash
# 1. Instance 2 có chạy không?
netstat -ano | findstr 9013

# 2. Service.instance.id có đúng không?
curl http://localhost:9013/health | jq .

# 3. Consul có chạy không?
curl http://localhost:8500/ui/
```

### Lỗi 4: Load Balancer không xen kẽ request
**Giải pháp:**
- Kiểm tra cả 2 instance có `/health` endpoint không
- Kiểm tra `X-Served-By` header có được thêm vào response không
- Xem log Gateway có in instance ID không

---

## 📚 Tài Liệu Liên Quan

- **MONITORING-SETUP.md** – Kiểm tra health qua /health, /healthz, /metrics
- **LOGGING-SETUP.md** – Xem log instance
- **docs/handoff/dot-5.md** – Script bật/tắt nhanh (optional)

---

## 🎯 Tóm Tắt Quick Start

```bash
# Terminal 1: Instance 1 (port 9003)
cd inventory-service
sbt "run 9003"

# Terminal 2: Instance 2 (port 9013)
cd inventory-service
sbt -Dservice.instance.id=inventory-2 "run 9013"

# Terminal 3: Test
curl http://localhost:8500/ui/  # Xem Consul UI
for i in {1..10}; do curl -s http://localhost:9000/health | jq . ; done

# Dừng: Ctrl+C trên mỗi terminal
```

---

**Hoàn thành!** Bây giờ bạn có 2 instance chạy song song, sẵn sàng để:
- ✅ Demo Service Discovery
- ✅ Demo Round-robin Load Balancing
- ✅ Demo Failover (tắt 1 instance, request sang instance kia)
