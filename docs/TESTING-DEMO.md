# 🧪 Hướng Dẫn Test Demo - 3 Tính Chất SOA

**Mục tiêu**: Chứng minh 3 tính chất của kiến trúc SOA:
1. ✅ **Service Discovery** - Services đăng ký Consul, Gateway tự động khám phá
2. ✅ **Load Balancing** - Request xen kẽ giữa Instance 1 ↔ Instance 2
3. ✅ **Fault Tolerance** - Tắt 1 instance, request tự động fail-over sang instance kia

**Thời gian**: ~30 phút  
**Yêu cầu**: Java, SBT, Node.js, **Consul**, SQL Server

---

## 📋 Bước 0: Chuẩn Bị Consul (BẮT BUỘC)

### Cách 1: Cài Đặt Consul Locally (Recommended)

**Windows:**
```powershell
# Option A: Tải manual từ https://www.consul.io/downloads
# 1. Download: consul_1.17.1_windows_amd64.zip
# 2. Extract vào: C:\Program Files\Consul
# 3. Thêm PATH environment variable

# Option B: Dùng Chocolatey
choco install consul

# Option C: Dùng Scoop
scoop install consul
```

**macOS:**
```bash
# Dùng Homebrew
brew install consul
```

**Linux:**
```bash
# Download
wget https://releases.hashicorp.com/consul/1.17.1/consul_1.17.1_linux_amd64.zip
unzip consul_1.17.1_linux_amd64.zip
sudo mv consul /usr/local/bin/
```

### Cách 2: Dùng Docker (Nếu có Docker)

```bash
# Khởi động Consul container
docker run -d \
  -p 8500:8500 \
  -p 8600:8600/udp \
  --name=consul \
  consul agent -server -ui \
  -bootstrap-expect=1 \
  -client=0.0.0.0

# Kiểm tra
curl http://localhost:8500/ui/
```

### Cách 3: Dùng WSL2 trên Windows

```bash
# Trong WSL2 terminal
wsl sudo apt-get update
wsl sudo apt-get install -y consul

# Hoặc download binary
wsl wget https://releases.hashicorp.com/consul/1.17.1/consul_1.17.1_linux_amd64.zip
wsl unzip consul_1.17.1_linux_amd64.zip
wsl sudo mv consul /usr/local/bin/
```

---

## 🚀 Bước 1: Khởi Động Consul

### Terminal 1: Consul Server

```bash
# Windows PowerShell (Run as Administrator)
consul agent -server -bootstrap -data-dir=./consul-data -ui

# macOS / Linux
consul agent -server -bootstrap -data-dir=./consul-data -ui
```

**Kỳ vọng:**
```
✓ Agent started successfully
✓ Listening on [TCP] 127.0.0.1:8500
✓ UI: http://localhost:8500/ui/
```

**Kiểm tra:**
```bash
curl http://localhost:8500/v1/catalog/services
# Kỳ vọng: {"consul":[]}
```

---

## 🚀 Bước 2: Khởi Động API Gateway

### Terminal 2: API Gateway (Port 9000)

```bash
cd api-gateway
sbt "run 9000"

# Kỳ vọng:
# [success] play.server.netty.NettyServer - Listening for HTTP on [::]:9000
```

**Kiểm tra:**
```bash
curl http://localhost:9000/health
# Kỳ vọng: {"status":"UP","service":"api-gateway"}
```

---

## 🚀 Bước 3: Khởi Động Service (2 Instances)

### Terminal 3: Auth Service - Instance 1 (Port 9001)

```bash
cd auth-service
sbt "run 9001"

# Kỳ vọng:
# [success] play.server.netty.NettyServer - Listening for HTTP on [::]:9001
```

**Kiểm tra:**
```bash
curl http://localhost:9001/health
# Kỳ vọng: {"status":"UP","service":"auth-service"}
```

### Terminal 4: Auth Service - Instance 2 (Port 9011)

```bash
cd auth-service
sbt -Dservice.instance.id=auth-2 -Dservice.port=9011 "run 9011"

# Kỳ vọng:
# [success] play.server.netty.NettyServer - Listening for HTTP on [::]:9011
```

**Kiểm tra:**
```bash
curl http://localhost:9011/health
# Kỳ vọng: {"status":"UP","service":"auth-service"}
```

---

## 🎯 Bước 4: Kiểm Tra 3 Tính Chất SOA

### Test 1️⃣: Service Discovery

**Terminal 5: Mở Consul UI**

```bash
# Trên browser
http://localhost:8500/ui/

# Hoặc CLI
curl http://localhost:8500/v1/catalog/services | jq .
```

**Kỳ vọng:**
```json
{
  "auth-service": [],
  "consul": []
}
```

**Xem chi tiết:**
```bash
curl http://localhost:8500/v1/catalog/service/auth-service | jq '.[] | {ID, ServicePort, Status}'

# Kỳ vọng:
# {
#   "ID": "auth-1",
#   "ServicePort": 9001,
#   "Status": "passing"
# }
# {
#   "ID": "auth-2",
#   "ServicePort": 9011,
#   "Status": "passing"
# }
```

✅ **DEMO**: 2 instances tự động đăng ký với Consul!

---

### Test 2️⃣: Load Balancing (Round-Robin)

**Terminal 5: Gửi 10 requests liên tục**

```bash
# Windows PowerShell
for ($i = 1; $i -le 10; $i++) {
    $response = Invoke-WebRequest -Uri "http://localhost:9000/health" -UseBasicParsing
    $servedBy = $response.Headers["X-Served-By"]
    Write-Host "Request $i → X-Served-By: $servedBy"
}

# Linux / macOS
for i in {1..10}; do
    echo "Request $i:"
    curl -i http://localhost:9000/health 2>/dev/null | grep -E "HTTP|X-Served-By"
done
```

**Kỳ vọng:**
```
Request 1 → X-Served-By: gateway-1
Request 2 → X-Served-By: gateway-1
Request 3 → X-Served-By: gateway-1
...

(Hoặc nếu request đi qua auth-service)
Request 1 → X-Served-By: auth-1
Request 2 → X-Served-By: auth-2
Request 3 → X-Served-By: auth-1
Request 4 → X-Served-By: auth-2
...
```

✅ **DEMO**: Load balancing xen kẽ instance-1 ↔ instance-2!

---

### Test 3️⃣: Failover (Fault Tolerance)

**Step 1: Gửi request liên tục**

```bash
# Terminal 5: Vòng lặp request 100 lần
while true; do
    curl -s http://localhost:9000/health | jq .
    sleep 1
done
```

**Step 2: Tắt Instance 1**

```bash
# Terminal 3 hoặc 4: Nhấn Ctrl+C để dừng Instance 1 (9001)
# Hoặc dùng lệnh:
taskkill /FI "WINDOWTITLE eq *9001*" /T

# Hoặc dùng script
.\scripts\stop-all.ps1 auth-1
```

**Kiểm tra Consul:**
```bash
curl http://localhost:8500/v1/health/service/auth-service?passing | jq '.[] | {ID, ServicePort}'

# Kỳ vọng: Chỉ còn auth-2 (9011)
# {
#   "ID": "auth-2",
#   "ServicePort": 9011
# }
```

**Kỳ vọng ở Terminal 5:**
```
✅ Lúc đầu: Mix auth-1 ↔ auth-2
⚠️  Sau 10 giây: Chuyển sang 100% auth-2
✅ Request vẫn thành công (NO ERRORS!)
```

**Step 3: Bật lại Instance 1**

```bash
# Terminal 3: Khởi động lại
sbt "run 9001"
```

**Kỳ vọng:**
```
✅ Instance 1 re-register với Consul
✅ Consul status → "passing"
✅ Load distribution quay lại Round-robin: auth-1 ↔ auth-2
```

---

## 📊 Test Health & Metrics Endpoints

### Health Check Endpoints

```bash
# Simple health
curl http://localhost:9000/health
# {"status":"UP","service":"api-gateway"}

# Detailed health
curl http://localhost:9000/healthz | jq .

# Prometheus metrics
curl http://localhost:9000/metrics | head -20
```

---

## 🛠️ Sử Dụng Scripts Bật/Tắt (Tự Động)

**Nếu không muốn chạy từng terminal:**

```bash
# Khởi động tất cả (2 instances)
bash scripts/start-all.sh

# Kiểm tra status
bash scripts/status.sh

# Hoặc PowerShell
.\scripts\start-all.ps1
.\scripts\status.ps1
.\scripts\stop-all.ps1
```

---

## 📸 Ảnh Chụp Kỳ Vọng

### Consul UI
```
Services: auth-service, consul
├── auth-1 (9001) ✅ Passing
└── auth-2 (9011) ✅ Passing
```

### Load Balancing Test
```
Request 1: X-Served-By: auth-1 ✅
Request 2: X-Served-By: auth-2 ✅
Request 3: X-Served-By: auth-1 ✅
Request 4: X-Served-By: auth-2 ✅
...
Distribution: 50% auth-1, 50% auth-2
```

### Failover Test
```
Tắt auth-1:
  [BEFORE] auth-1: 50% ├── [AFTER] auth-2: 100% (no errors!)
           auth-2: 50% └──
  
Bật lại auth-1:
  [BEFORE] auth-2: 100% ├── [AFTER] auth-1: 50%
                        └── auth-2: 50%
```

---

## ✅ Checklist Kiểm Chứng

- [ ] **Consul** đang chạy (http://localhost:8500/ui/)
- [ ] **Gateway** khởi động port 9000 ✅
- [ ] **Auth Service Instance 1** khởi động port 9001 ✅
- [ ] **Auth Service Instance 2** khởi động port 9011 ✅
- [ ] Consul UI thấy 2 instances passing ✅
- [ ] 10 requests có X-Served-By header xen kẽ ✅
- [ ] Tắt Instance 1 → request vẫn thành công ✅
- [ ] Bật lại Instance 1 → load distribution trở lại 50-50 ✅
- [ ] Mở http://localhost:8080 → Frontend load (nếu chạy) ✅

---

## 🆘 Troubleshooting

### Consul không chạy
```bash
# Kiểm tra
curl http://localhost:8500/ui/

# Nếu lỗi: "Access denied" → Chạy terminal as Administrator
# Nếu lỗi: "Connection refused" → Consul chưa khởi động

# Khởi động lại
consul agent -server -bootstrap -data-dir=./consul-data -ui
```

### Port đang dùng
```bash
# Windows: Kiểm tra
netstat -ano | findstr :9001

# Kill process
taskkill /PID <PID> /F

# macOS / Linux
lsof -i :9001
kill -9 <PID>
```

### Service không đăng ký Consul
```bash
# Kiểm tra log service
# Tìm "[ConsulModule]" hoặc "Registered with Consul"

# Check health endpoint trực tiếp
curl http://localhost:9001/health

# Check Consul API
curl http://localhost:8500/v1/catalog/service/auth-service | jq .
```

### Load Balancing không xen kẽ
```bash
# Kiểm tra cả 2 instances có chạy không
netstat -ano | findstr ":900"

# Kiểm tra X-Served-By header được thêm vào không
curl -i http://localhost:9000/health | grep X-Served-By
```

---

## 📚 Tài Liệu Liên Quan

- `docs/QUICK-START.md` - Khởi chạy nhanh
- `docs/INSTANCE-2-SETUP.md` - Chi tiết Instance 2
- `docs/MONITORING-SETUP.md` - Health & Metrics endpoints
- `docs/handoff/dot-*.md` - Chi tiết từng đợt
- `scripts/start-all.sh` / `start-all.ps1` - Script tự động

---

## 🎬 Demo Live (Sequence)

```
T=0:00   Start Consul
         ↓
T=0:10   Start Gateway (9000)
         ↓
T=0:20   Start Auth Instance 1 (9001)
         ↓
T=0:30   Start Auth Instance 2 (9011)
         ↓
T=0:40   Open Consul UI → See 2 instances ✅ DISCOVERY
         ↓
T=0:50   Run load balancing test (10 requests)
         → X-Served-By xen kẽ auth-1 ↔ auth-2 ✅ LOAD BALANCING
         ↓
T=1:00   Kill Auth Instance 1 (9001)
         ↓
T=1:10   Run 100 requests loop
         → 100% success (fail-over to auth-2) ✅ FAILOVER
         ↓
T=1:20   Restart Auth Instance 1 (9001)
         ↓
T=1:30   Load distribution back to 50-50 ✅ RECOVERY
```

---

**Hoàn thành! Bạn vừa chứng minh được 3 tính chất SOA: Service Discovery, Load Balancing, Fault Tolerance** 🎊
