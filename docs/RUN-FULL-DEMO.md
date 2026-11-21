# 🎬 Hướng Dẫn Chạy Full Demo - Toàn Bộ Hệ Thống

**Mục tiêu**: Khởi động **tất cả 14 processes** và kiểm tra hoạt động

---

## 📊 Hệ Thống Gồm

| Thành Phần | Instance | Cổng | Số Lượng |
|-----------|----------|------|----------|
| **API Gateway** | 1 | 9000 | 1 |
| **Auth Service** | 2 | 9001, 9011 | 2 |
| **Catalog Service** | 2 | 9002, 9012 | 2 |
| **Inventory Service** | 2 | 9003, 9013 | 2 |
| **Purchase Service** | 2 | 9004, 9014 | 2 |
| **Sales Service** | 2 | 9005, 9015 | 2 |
| **Report Service** | 2 | 9006, 9016 | 2 |
| **Frontend** | 1 | 8080 | 1 |
| | | **TOTAL** | **14** |

---

## 🚀 Chạy Full Demo (1 Lệnh)

### Cách 1: Windows PowerShell (RECOMMENDED)

```powershell
# Khởi động 1 instance/service (nhanh, tiết kiệm RAM)
.\scripts\demo-soa-instances.ps1

# Hoặc khởi động 2 instances/service (demo load balancing)
.\scripts\demo-soa-instances.ps1 -Instances 2

# Bỏ qua kiểm tra Consul (nếu không có Consul)
.\scripts\demo-soa-instances.ps1 -SkipConsulCheck
```

### Cách 2: Linux / macOS (Bash)

```bash
# Khởi động 1 instance/service
bash scripts/demo-soa-instances.sh

# Khởi động 2 instances/service
bash scripts/demo-soa-instances.sh --instances 2

# Bỏ qua Consul
bash scripts/demo-soa-instances.sh --skip-consul
```

---

## 📋 Quá Trình Demo

### Step 1: Kiểm Tra Điều Kiện Tiên Quyết (1 phút)

```
✅ Java 11+
✅ SBT
✅ Node.js
⚠️ Consul (optional - nếu có sẽ test Service Discovery)
```

### Step 2: Khởi Động Gateway (2 phút)

```
🚀 Starting API Gateway...
  PID: 12345
  Waiting for health check...
  ........................
  ✅ API Gateway (port 9000) ready
```

### Step 3: Khởi Động 6 Services (15-20 phút)

```
🚀 Starting auth-service...
  Instance 1 (port 9001)...
    PID: 12346
    ........................
    ✅ auth-service-1 (port 9001) ready
  Instance 2 (port 9011)...
    PID: 12347
    ........................
    ✅ auth-service-2 (port 9011) ready

🚀 Starting catalog-service...
  ...
  
... (tương tự cho 4 services còn lại)
```

### Step 4: Khởi Động Frontend (2 phút)

```
🚀 Starting Frontend...
  Installing npm packages...
  PID: 12348
  Waiting for health check...
  ........................
  ✅ Frontend (port 8080) ready
```

### Step 5: Tóm Tắt Kết Quả

```
════════════════════════════════════════════════════════════════

📊 All Services Status:

API Gateway:
  ✅ gateway-1 (9000)

Services:
  ✅ auth-service-1 (9001)
  ✅ auth-service-2 (9011)
  ✅ catalog-service-1 (9002)
  ✅ catalog-service-2 (9012)
  ✅ inventory-service-1 (9003)
  ✅ inventory-service-2 (9013)
  ✅ purchase-service-1 (9004)
  ✅ purchase-service-2 (9014)
  ✅ sales-service-1 (9005)
  ✅ sales-service-2 (9015)
  ✅ report-service-1 (9006)
  ✅ report-service-2 (9016)

Frontend:
  ✅ frontend-1 (8080)

════════════════════════════════════════════════════════════════
📊 Total: 14 / 14 running

🔗 Quick Links:
  Gateway Health:    http://localhost:9000/health
  Gateway Metrics:   http://localhost:9000/metrics
  Consul UI:         http://localhost:8500/ui/
  Frontend:          http://localhost:8080

📋 Test Commands:
  Load Balancing:    .\scripts\test-load-balancing.ps1
  Check Health:      .\scripts\check-health.ps1
  View Status:       .\scripts\status.ps1 -Watch
  Stop All:          .\scripts\stop-all.ps1

════════════════════════════════════════════════════════════════
✅ ALL SYSTEMS OPERATIONAL!
```

---

## ✅ Kiểm Chứng Hoạt Động

### 1️⃣ Health Check (Immediate)

```bash
# Check Gateway
curl http://localhost:9000/health
# Kỳ vọng: {"status":"UP","service":"api-gateway"}

# Check một service
curl http://localhost:9001/health
# Kỳ vọng: {"status":"UP","service":"auth-service"}

# Check Frontend
curl http://localhost:8080/health
# Kỳ vọng: 200 OK
```

### 2️⃣ Detailed Health

```bash
# Chi tiết health
curl http://localhost:9000/healthz | jq .

# Kỳ vọng: JSON có jvm, circuitBreakers, bulkheads status
```

### 3️⃣ Metrics

```bash
# Xem metrics (Prometheus format)
curl http://localhost:9000/metrics | head -30

# Kỳ vọng: Các dòng HELP, TYPE, + metrics values
```

### 4️⃣ Load Balancing Test

```powershell
# Test 10 requests
.\scripts\test-load-balancing.ps1 -RequestCount 10

# Kỳ vọng: X-Served-By xen kẽ giữa các instance
```

### 5️⃣ Service Discovery (nếu có Consul)

```bash
# Xem Consul UI
http://localhost:8500/ui/

# Hoặc CLI
curl http://localhost:8500/v1/catalog/services | jq .

# Kỳ vọng: Tất cả 6 services + 12 instances registered
```

### 6️⃣ Frontend

```bash
# Mở frontend
http://localhost:8080

# Kỳ vọng:
# - Trang chủ load thành công
# - Bảng Server-Cổng hiển thị
# - Các nút test (Check Health, View Metrics, Load Balancing Test)
# - Links đến Consul UI, Gateway, API endpoints
```

---

## 📊 Yêu Cầu Tài Nguyên

### RAM Sử Dụng (Ước Tính)

| Config | RAM | Ghi Chú |
|--------|-----|--------|
| **1 instance/service** | 3-4 GB | Recommended (dev/test) |
| **2 instances/service** | 6-8 GB | Full demo (load balancing) |

### Thời Gian Khởi Động

| Giai Đoạn | Thời Gian |
|-----------|----------|
| Prerequisites check | 1-2 phút |
| Gateway startup | 2-3 phút |
| 6 Services (1 instance each) | 10-15 phút |
| 6 Services (2 instances each) | 15-25 phút |
| Frontend startup | 1-2 phút |
| **TOTAL (1 instance)** | **~15-20 phút** |
| **TOTAL (2 instances)** | **~25-35 phút** |

---

## 📁 Log Files

Tất cả log được lưu ở `logs/` folder:

```
logs/
├── gateway-1.log
├── auth-service-1.log
├── auth-service-2.log
├── catalog-service-1.log
├── catalog-service-2.log
├── inventory-service-1.log
├── inventory-service-2.log
├── purchase-service-1.log
├── purchase-service-2.log
├── sales-service-1.log
├── sales-service-2.log
├── report-service-1.log
├── report-service-2.log
└── frontend-1.log
```

**Để xem log real-time:**

```bash
# PowerShell
Get-Content logs\auth-service-1.log -Wait

# Linux / macOS
tail -f logs/auth-service-1.log
```

---

## 🆘 Troubleshooting

### RAM Không Đủ

```bash
# Giảm xung yêu cầu: 1 instance/service
.\scripts\demo-soa-instances.ps1 -Instances 1
```

### Port Đang Dùng

```bash
# Kiểm tra cổng
netstat -ano | findstr :9001

# Nếu cổng bị chiếm, dừng processes cũ
.\scripts\stop-all.ps1
```

### Service Không Khởi Động

```bash
# Check log của service đó
Get-Content logs\auth-service-1.log

# Tìm error (nếu có)
# Có thể là: Port conflict, Database connection, Consul unavailable
```

### Consul Không Chạy

```bash
# Nếu không có Consul, bỏ qua
.\scripts\demo-soa-instances.ps1 -SkipConsulCheck

# Hoặc khởi động Consul
consul agent -server -bootstrap -data-dir=./consul-data -ui
```

---

## 🎯 Tiếp Theo (Sau Khi All Systems Operational)

### 1️⃣ Test Load Balancing

```powershell
.\scripts\test-load-balancing.ps1 -RequestCount 20
```

### 2️⃣ Test Failover

```bash
# Tắt 1 instance
.\scripts\stop-all.ps1 auth-1

# Run 100 requests → xem fail-over thành công không
```

### 3️⃣ Mở Frontend

```
http://localhost:8080
```

### 4️⃣ Mở Consul UI (nếu có)

```
http://localhost:8500/ui/
```

---

## 📝 Cheatsheet Commands

```powershell
# Khởi động full demo
.\scripts\demo-soa-instances.ps1

# Kiểm tra health tất cả
.\scripts\check-health.ps1

# Xem status real-time
.\scripts\status.ps1 -Watch

# Test load balancing
.\scripts\test-load-balancing.ps1 -RequestCount 10

# Dừng tất cả
.\scripts\stop-all.ps1

# Dừng 1 instance (demo failover)
.\scripts\stop-all.ps1 -Instance "auth-1"

# Khởi động lại
.\scripts\start-all.ps1
```

---

## ✅ Success Criteria

Hệ thống thành công khi:

- ✅ Tất cả 14 services khởi động thành công
- ✅ Tất cả ports libre (9000, 9001-9006, 9011-9016, 8080)
- ✅ Tất cả health endpoints trả 200 OK
- ✅ Gateway metrics endpoint có dữ liệu
- ✅ Frontend tải thành công
- ✅ Consul UI (nếu có) thấy tất cả services registered
- ✅ Load balancing test: X-Served-By xen kẽ
- ✅ Tắt 1 instance: request vẫn thành công (fail-over)

---

## 📚 Tài Liệu Liên Quan

- `docs/TESTING-DEMO.md` - Hướng dẫn test chi tiết
- `docs/QUICK-START.md` - Khởi động nhanh
- `docs/INSTANCE-2-SETUP.md` - Cấu hình Instance 2
- `scripts/start-all.ps1` - Khởi động từng cái
- `scripts/stop-all.ps1` - Dừng từng cái
- `scripts/status.ps1` - Xem status

---

## 🎊 Tóm Tắt

```powershell
# 1 lệnh khởi động tất cả 14 processes
.\scripts\demo-soa-instances.ps1

# 1 lệnh để xem status tất cả
.\scripts\status.ps1

# 1 lệnh để dừng tất cả
.\scripts\stop-all.ps1
```

**Ready to demo? Let's go! 🚀**
