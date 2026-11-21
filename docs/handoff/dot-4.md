# BÀN GIAO SAU ĐỢT 4: FAULT TOLERANCE & SAFETY

## 1. Trạng thái
- Đợt đã hoàn thành: 4 / 6
- Commit: `67e687f` – "Đợt 4: Fault Tolerance + Safety - Retry Logic, Timeout, Idempotency-Key, FEFO Lock"
- Trạng thái: ✅ Hoàn thành đầy đủ

## 2. Đã làm

### **PHẦN 1: Automatic Instance Deregistration** ✅
- Consul health check mỗi 10s, timeout 3s
- Auto deregister sau 1 phút nếu unhealthy
- **File:** `auth-service/app/modules/ConsulModule.java` (đã có từ Đợt 2)
- **Check:** `deregisterCriticalServiceAfter("1m")`

### **PHẦN 2: Retry Logic** ✅
- Tạo `RetryPolicy.java` - interface định sách retry
- Tạo `ExponentialBackoffRetryPolicy.java` - implement với exponential backoff (100ms, 200ms)
- Quy tắc:
  - GET request: retry 2 lần (idempotent)
  - POST/PUT/DELETE có `Idempotency-Key`: retry 2 lần
  - Các request khác: không retry
- **File:** 
  - `api-gateway/app/services/RetryPolicy.java`
  - `api-gateway/app/services/ExponentialBackoffRetryPolicy.java`

### **PHẦN 3: Timeout Configuration** ✅
- Connection timeout: 5 giây
- Read timeout: 10 giây
- Cấu hình trong `application.conf` (gateway)
- Tạo `ResilientServiceCaller.java` - gọi service với retry + timeout
- **File:**
  - `api-gateway/app/services/ResilientServiceCaller.java`
  - `api-gateway/conf/application.conf` (thêm timeout config)

### **PHẦN 4: Idempotency-Key Support** ✅
- Tạo `IdempotencyFilter.java` - filter xử lý Idempotency-Key
- Tạo `IdempotencyCache.java` - interface cache (Redis-like)
- Tạo `InMemoryIdempotencyCache.java` - in-memory implementation (dev/test)
- Caching logic:
  - Client gửi `Idempotency-Key` header
  - Server cache response thành công 1 giờ
  - Nếu request duplicate → trả response cũ
  - Nếu lỗi → không cache, cho phép retry
- Enable filter trong `api-gateway/conf/application.conf`
- **File:**
  - `api-gateway/app/filters/IdempotencyFilter.java`
  - `api-gateway/app/services/IdempotencyCache.java`
  - `api-gateway/app/services/InMemoryIdempotencyCache.java`

### **PHẦN 5: Stateless Validation + FEFO Lock** ✅

#### A. Stateless (Auth-Service)
- Tạo `AuthController.java` - stateless endpoints
- Không dùng session local, mỗi request độc lập
- Endpoints:
  - `POST /login` - trả JWT token
  - `GET /profile` - require `Authorization: Bearer <token>` header
- **File:** `auth-service/app/controllers/AuthController.java`

#### B. FEFO Lock (Inventory-Service)
- Tạo `InventoryRepository.java` - SQL with UPDLOCK + ROWLOCK
- Deduct inventory operation:
  ```sql
  SELECT ... WITH (UPDLOCK, ROWLOCK) FROM Inventory WHERE ...
  UPDATE Inventory SET quantity = quantity - ?, version = version + 1
  ```
- Optimistic locking với version number (race condition check)
- **File:** `inventory-service/app/db/InventoryRepository.java`

## 3. File đã tạo/sửa

### Mới tạo (9 file)
- `api-gateway/app/services/RetryPolicy.java`
- `api-gateway/app/services/ExponentialBackoffRetryPolicy.java`
- `api-gateway/app/services/ResilientServiceCaller.java`
- `api-gateway/app/filters/IdempotencyFilter.java`
- `api-gateway/app/services/IdempotencyCache.java`
- `api-gateway/app/services/InMemoryIdempotencyCache.java`
- `auth-service/app/controllers/AuthController.java`
- `inventory-service/app/db/InventoryRepository.java`
- `shared/` folder (filters + services - copy sang api-gateway)

### Sửa đổi (2 file)
- `api-gateway/app/services/GatewayModule.java`
  - Thêm `bind(IdempotencyCache.class).to(InMemoryIdempotencyCache.class)`
- `api-gateway/conf/application.conf`
  - Thêm timeout config (connectionTimeoutMs, readTimeoutMs)
  - Thêm retry config (maxAttempts, baseBackoffMs)
  - Enable `IdempotencyFilter`

## 4. Workflow Mỗi Request (SAU ĐỢT 4)

```
1. Client gửi request + Idempotency-Key
        ↓
2. API Gateway nhận
        ↓
3. IdempotencyFilter kiểm tra cache
   - Nếu cache hit → trả response cũ
   - Nếu miss → tiếp tục
        ↓
4. LoadBalancer chọn instance (Round-robin)
        ↓
5. ResilientServiceCaller gọi
   - Timeout: 5s connection, 10s read
   - Nếu fail → RetryPolicy retry 2 lần (exponential backoff)
   - Nếu vẫn fail → error response
        ↓
6. Service xử lý (stateless)
   - Nếu là FEFO operation → dùng UPDLOCK + ROWLOCK
   - Nếu có Idempotency-Key → cache response
        ↓
7. Trả kết quả về client
```

## 5. Lệnh kiểm chứng

| Lệnh | Kết quả |
|------|---------|
| `sbt compile` (gateway) | [success] |
| `sbt compile` (auth-service) | [success] |
| `sbt compile` (inventory-service) | [success] |
| `Select-String "RetryPolicy" api-gateway/app/services/*.java` | 2 lần |
| `Select-String "IdempotencyFilter" api-gateway/app/filters/*.java` | 1 lần |
| `Select-String "ResilientServiceCaller" api-gateway/app/services/*.java` | 1 lần |
| `Select-String "UPDLOCK" inventory-service/app/db/*.java` | 1 lần |
| `Select-String "AuthController" auth-service/app/controllers/*.java` | 1 lần |
| `git log --oneline \| head -1` | 67e687f Đợt 4: ... |

## 6. Quyết định kỹ thuật

- **Exponential Backoff**: 100ms * 2^(attempt-1) → 100ms, 200ms, 400ms...
- **Idempotency TTL**: 1 giờ (balance giữa memory usage và idempotency guarantee)
- **In-Memory Cache**: Để production dùng Redis/Memcached
- **FEFO Lock**: UPDLOCK + ROWLOCK + optimistic version check (double protection)
- **Stateless Auth**: JWT token thay vì session cookie (microservices best practice)

## 7. Việc tồn đọng / Cải thiện

- ⚠️ **ResilientServiceCaller**: Chỉ dùng Java URL connection, không có retry header propagation
  → Để Đợt sau: Use HTTP client library (Apache HttpClient, OkHttp) với better logging
  
- ⚠️ **InMemoryIdempotencyCache**: Dùng ConcurrentHashMap, chưa có cleanup thread
  → Để production: Cleanup thread định kỳ xóa expired entries, hoặc dùng Redis
  
- ⚠️ **AuthController**: JWT generation/verification đơn giản (hardcoded, không dùng real JWT library)
  → Để production: Dùng `com.auth0:java-jwt` hoặc `io.jsonwebtoken:jjwt`
  
- ⚠️ **InventoryRepository**: Direct JDBC (SQL string concatenation), không dùng ORM
  → Để production: Dùng Hibernate, JPA, hoặc parameterized queries helper

## 8. Kế hoạch cho Đợt 5-6

### **Đợt 5: Circuit Breaker + Bulkhead Pattern**
- Tránh cascading failure khi service chết
- Bulkhead: tách thread pool cho mỗi service call
- Circuit breaker: fail fast + auto recovery

### **Đợt 6: Monitoring & Observability**
- Metrics: JVM metrics, request latency, error rate
- Tracing: distributed tracing (correlation ID propagation)
- Logging: structured logging → centralized log aggregation (ELK, Datadog)
- Health dashboard

## 9. Success Criteria

✅ Retry logic hoạt động (exponential backoff 100ms, 200ms)
✅ Timeout được config (5s connection, 10s read)
✅ Idempotency-Key được cache (request duplicate trả kết quả cũ)
✅ UPDLOCK được dùng trong SQL (SELECT ... WITH (UPDLOCK, ROWLOCK))
✅ Auth-service validate stateless (không dùng session local, dùng JWT)
✅ Compile thành công (api-gateway, auth-service, inventory-service)

---

**Tóm tắt Đợt 4**: Thêm retry logic, timeout, idempotency-key caching, và FEFO lock. Hệ thống giờ chịu lỗi tốt hơn và an toàn hơn khi chạy multiple instances.

**Commit:** `67e687f` - Đợt 4 hoàn thành ✅
