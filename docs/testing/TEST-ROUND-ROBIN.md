# Test Round-Robin Load Balancing

Hướng dẫn test cân bằng tải Round-robin giữa 2 instance cùng service.

---

## Điều kiện cần
- ✓ SQL Server chạy trên `localhost:1433`
- ✓ Consul chạy trên `localhost:8500`
- ✓ Database `PharmacyManagementSOA` đã tạo

---

## Bước 1: Khởi động Consul

```powershell
# Docker
docker run -p 8500:8500 -p 8600:8600/udp --name consul -d consul agent -server -ui -bootstrap-expect=1 -client=0.0.0.0

# Kiểm tra Consul UI: http://localhost:8500
```

---

## Bước 2: Khởi động API Gateway

**Terminal 1:**
```powershell
cd d:\Java\PharmacyManagementSOA\api-gateway
sbt -Dservice.instance.id=gateway-1 "run 9000"
```

Chờ log thấy:
```
[info] ✓ [gateway-1] Đã đăng ký lên Consul: api-gateway (localhost:9000)
```

---

## Bước 3: Khởi động 2 instance Auth Service

**Terminal 2:**
```powershell
cd d:\Java\PharmacyManagementSOA\auth-service
sbt -Dservice.instance.id=auth-1 "run 9001"
```

Chờ log:
```
[info] ✓ [auth-1] Đã đăng ký lên Consul: auth-service (localhost:9001)
```

**Terminal 3:**
```powershell
cd d:\Java\PharmacyManagementSOA\auth-service
sbt -Dservice.instance.id=auth-2 "run 9011"
```

Chờ log:
```
[info] ✓ [auth-2] Đã đăng ký lên Consul: auth-service (localhost:9011)
```

---

## Bước 4: Kiểm tra Consul UI

Mở browser: http://localhost:8500/ui/dc1/services

Nên thấy:
- `api-gateway`: 1 instance (gateway-1)
- `auth-service`: 2 instance (auth-1, auth-2) **cả hai passing**

---

## Bước 5: Test Round-robin (PowerShell)

**Terminal 4:**
```powershell
# Gọi health endpoint 10 lần liên tiếp
# Mỗi lần ghi ra X-Served-By header (cổng nào phục vụ)

Write-Host "Testing Round-robin Load Balancing..." -ForegroundColor Green
Write-Host ""

$results = @()
for ($i = 1; $i -le 10; $i++) {
    $response = curl -s -i http://localhost:9000/health 2>&1 | Out-String
    
    # Tìm X-Served-By header
    $servedBy = ($response | Select-String "X-Served-By:").Line
    
    if ($servedBy) {
        $results += $servedBy
        Write-Host "Request $i`: $servedBy" -ForegroundColor Cyan
    } else {
        Write-Host "Request $i`: NO X-Served-By HEADER (ERROR)" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "=== Summary ===" -ForegroundColor Green

# Đếm gateway-1
$count = ($results | Select-String "gateway-1" | Measure-Object).Count
Write-Host "Gateway-1: $count times"
```

---

## Bước 6: Kết quả mong đợi

```
Testing Round-robin Load Balancing...

Request 1: X-Served-By: gateway-1
Request 2: X-Served-By: gateway-1
...
```

**Lưu ý**: Gateway luôn trả `gateway-1` vì chỉ có 1 instance gateway.

---

## Test Round-robin giữa 2 Auth Instances

Để test thực sự Round-robin, ta cần gateway gọi auth-service:

```powershell
# Giả sử gateway có endpoint proxy tới auth-service
# Ví dụ: GET /api/v1/auth/status

for ($i = 1; $i -le 10; $i++) {
    $response = curl -s -i http://localhost:9000/api/v1/auth/status 2>&1 | Out-String
    $servedBy = ($response | Select-String "X-Served-By:").Line
    Write-Host "Request $i`: $servedBy"
}
```

**Kết quả mong đợi**: Luân phiên giữa `X-Served-By: auth-1` và `X-Served-By: auth-2`

---

## Xem Log File

```powershell
# Log gateway
Get-Content -Wait d:\Java\PharmacyManagementSOA\api-gateway\logs\gateway.log

# Log auth-service instance 1
Get-Content -Wait d:\Java\PharmacyManagementSOA\auth-service\logs\auth-service.log
```

---

## Tắt tất cả

Nhấn `Ctrl+C` ở mỗi terminal để dừng service.

```powershell
# Tắt Consul container (nếu dùng Docker)
docker stop consul
```

---

## Troubleshooting

### Consul không thấy instance
- Kiểm tra Consul log: `docker logs consul`
- Kiểm tra service startup log có `"Đã đăng ký lên Consul"` không?

### X-Served-By header không có
- Kiểm tra gateway chạy chưa
- Kiểm tra filter được enable trong `application.conf`

### Connection refused (9000, 9001, 9011)
- Kiểm tra service chạy chưa
- Kiểm tra port có bị chiếm không: `netstat -an | Select-String ":9000"`

---

**Thành công khi thấy `X-Served-By` header trong response!**
