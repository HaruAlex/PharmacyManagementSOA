# 🚀 Quick Start - Chạy Hệ Thống Pharmacy Management SOA

**Thời gian:** ~10 phút từ lần đầu  
**Yêu cầu:** Java 11+, SBT, Node.js 14+, Consul, SQL Server  

---

## ⏱️ Bước 1: Chuẩn Bị (2 phút)

### Kiểm tra điều kiện tiên quyết

```bash
# Java
java -version          # JDK 11+

# SBT
sbt --version          # 1.10+

# Node.js
node --version         # 14+
npm --version          # 6+

# Consul (phải chạy sẵn)
curl http://localhost:8500/ui/  # Trả 200 OK

# SQL Server (phải chạy sẵn)
# Kết nối bằng management tools hoặc cmd
sqlcmd -S localhost -U sa -P <password>
```

### Tạo Database (nếu chưa có)

```sql
-- SQL Server
CREATE DATABASE PharmacyDB;
GO

USE PharmacyDB;
GO

-- Bảng Inventory (ví dụ)
CREATE TABLE inventory (
    id INT PRIMARY KEY,
    product_id INT,
    quantity INT,
    warehouse_id INT
);
GO
```

---

## ⏱️ Bước 2: Khởi Động Services (5 phút)

### Thứ tự khởi động:

1. **Gateway** (port 9000)
2. **Services** (port 9001-9006)
3. **Frontend** (port 8080)

### Phương pháp 1: Manual - Chạy từng Terminal

#### Terminal 1: API Gateway (port 9000)

```bash
cd d:\Java\PharmacyManagementSOA\api-gateway
sbt "run 9000"

# Chờ đến khi thấy:
# [info] play.server.netty.NettyServer - Listening for HTTP on [::]:9000
```

#### Terminal 2: Auth Service (port 9001)

```bash
cd d:\Java\PharmacyManagementSOA\auth-service
sbt "run 9001"

# Tương tự cho các services khác:
# catalog-service: sbt "run 9002"
# inventory-service: sbt "run 9003"
# purchase-service: sbt "run 9004"
# sales-service: sbt "run 9005"
# report-service: sbt "run 9006"
```

#### Terminal 3: Frontend (port 8080)

```bash
cd d:\Java\PharmacyManagementSOA\frontend

# Lần đầu: cài npm packages
npm install

# Chạy
npm start

# Hoặc
PORT=8080 npm start
```

---

## ✅ Bước 3: Xác Minh Hệ Thống (2 phút)

### 1. Kiểm tra từng service

```bash
# Gateway health
curl http://localhost:9000/health
# ✅ Kỳ vọng: {"status":"UP","service":"api-gateway"}

# Auth service health
curl http://localhost:9001/health
# ✅ Kỳ vọng: {"status":"UP","service":"auth-service"}

# Tương tự cho các services khác (9002-9006)
```

### 2. Kiểm tra Consul

```bash
# CLI
consul catalog services

# HTTP API
curl http://localhost:8500/v1/catalog/service/auth-service | jq .

# Web UI
# Mở: http://localhost:8500/ui/
# Kiểm tra: Mỗi service có status "passing"
```

### 3. Kiểm tra Frontend

```bash
# Chạy từ browser
curl http://localhost:8080
# ✅ Kỳ vọng: Trả HTML (200 OK)

# Hoặc mở trực tiếp
# http://localhost:8080
```

### 4. Test Gateway Integration

```bash
# Gọi qua Gateway (không gọi trực tiếp service)
curl http://localhost:9000/api/v1/auth/health

# Kiểm tra X-Served-By header
curl -i http://localhost:9000/api/v1/inventory/stock
# ✅ Kỳ vọng: Header "X-Served-By: auth-1" hoặc service instance tương ứng
```

---

## 🧪 Bước 4: Demo 3 Tính Chất SOA (3 phút)

### 1. Service Discovery

```bash
# Mở Consul UI: http://localhost:8500/ui/
# Nhấn "Services" → Kiểm tra:
# - auth-service: PASSING ✅
# - catalog-service: PASSING ✅
# - ... (6 services)

# Hoặc dùng API
curl http://localhost:8500/v1/health/service/inventory-service?passing | jq '.[] | {Service, ServicePort}'

# Output ví dụ:
# {
#   "Service": "inventory-service",
#   "ServicePort": 9003
# }
```

### 2. Load Balancing

```bash
# Gửi 5 request liên tiếp
for i in {1..5}; do
  echo "Request $i:"
  curl -i http://localhost:9000/health 2>/dev/null | grep -E "X-Served|HTTP"
done

# Kỳ vọng: X-Served-By sẽ hiện instance ID (gateway-1, auth-1, v.v.)
# Nếu có Instance 2 chạy: sẽ thấy xen kẽ instance-1 và instance-2
```

### 3. Failover (Chịu Lỗi)

```bash
# Giải pháp 1: Tắt 1 terminal (service)
# Ví dụ: Đóng Terminal của auth-service (port 9001)

# Giải pháp 2: Hoặc dùng curl gọi /stop (nếu endpoint có)
# curl -X POST http://localhost:9001/admin/stop

# Kiểm tra Consul UI
# - Service bị tắt sẽ chuyển sang "CRITICAL" sau ~10 giây
# - Request qua Gateway vẫn thành công (fail-over sang instance khác)

# Bật lại
# - Service sẽ tự động re-register
# - Trở lại "PASSING" trong Consul
```

---

## 📊 Bước 5: Kiểm tra Metrics (Optional)

```bash
# Xem metrics Prometheus format
curl http://localhost:9000/metrics | head -30

# Xem health chi tiết
curl http://localhost:9000/healthz | jq .

# Hoặc mở Frontend
# http://localhost:8080
# → Nhấn "View Metrics" hoặc "Check All Services"
```

---

## 🛑 Dừng Hệ Thống

```bash
# Tắt từng terminal theo thứ tự ngược:
# 1. Frontend (Ctrl+C)
# 2. Services (Ctrl+C từng cái)
# 3. Gateway (Ctrl+C)

# Hoặc kill toàn bộ process
# Windows PowerShell
Get-Process | Where-Object { $_.Name -like "*java*" } | Stop-Process -Force
Get-Process node | Stop-Process -Force

# Linux / macOS
pkill -f "java.*sbt"
pkill node
```

---

## 🐳 Phương pháp 2: Docker Compose (Advanced)

Nếu muốn dùng Docker (tương lai):

```bash
# Build & run all
docker-compose up -d

# Scale service
docker-compose up -d --scale auth-service=2

# Kiểm tra
docker-compose ps
docker-compose logs -f auth-service

# Dừng
docker-compose down
```

*(Đợt tiếp theo có thể thêm docker-compose.yml)*

---

## 📚 Tài Liệu Chi Tiết

| Tài Liệu | Mục Đích |
|----------|----------|
| **INSTANCE-2-SETUP.md** | Chạy 2 instance/service (Round-robin demo) |
| **MONITORING-SETUP.md** | Cấu hình monitoring & metrics |
| **LOGGING-SETUP.md** | Structured logging |
| **docs/handoff/dot-*.md** | Chi tiết từng đợt (1-6) |

---

## ❓ Troubleshooting

### Port đang dùng
```bash
# Windows: Kiểm tra tiến trình
netstat -ano | findstr 9000

# Kill process
taskkill /PID <PID> /F
```

### Service không đăng ký Consul
```bash
# Kiểm tra log service
# Tìm "[ConsulModule]" hoặc "[ServiceRegistry]"

# Kiểm tra Consul API
curl http://localhost:8500/v1/health/service/auth-service?passing
# Nếu rỗng → service chưa đăng ký
```

### Frontend không load
```bash
# Kiểm tra Node.js
node --version

# Cài lại npm packages
cd frontend
npm install
npm start
```

### Gateway không forward request
```bash
# Kiểm tra Gateway log
# Tìm "[LoadBalancer]" hoặc "[ResilientServiceCaller]"

# Kiểm tra direct
curl http://localhost:9000/health
curl http://localhost:9000/api/v1/inventory/stock
```

---

## 🎉 Kết Quả Mong Đợi

✅ Tất cả terminal chạy không lỗi  
✅ Consul UI: 6 services + Gateway (7 services, mỗi cái passing)  
✅ Frontend: http://localhost:8080 hiển thị bảng services  
✅ Load Balancing: X-Served-By header xuất hiện  
✅ Metrics: http://localhost:9000/metrics trả Prometheus text  

---

## 📞 Hỗ Trợ

Gặp lỗi? Kiểm tra:

1. Tất cả services đã đăng ký Consul chưa?
   - `curl http://localhost:8500/v1/catalog/services`

2. Tất cả cổng có libre (không bị chiếm) không?
   - `netstat -ano | findstr <port>`

3. Log gateway có in gì?
   - Xem terminal Gateway khi gửi request

---

**Hoàn thành Quick Start! 🎊**

Tiếp theo: Đọc INSTANCE-2-SETUP.md để demo Round-robin Load Balancing
