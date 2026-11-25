# Auth Service - Pharmacy Management SOA

Service xác thực và phân quyền cho hệ thống Quản lý nhà thuốc.

## Yêu cầu hệ thống

- **Java**: JDK 17 trở lên
- **sbt**: 1.9.x trở lên
- **SQL Server**: 2019 trở lên
- **Database**: PharmacyManagementSOA (đã chạy scripts 01, 02)

## Cấu hình

### Biến môi trường

```bash
# Database connection
DATABASE_URL="jdbc:sqlserver://localhost:1433;databaseName=PharmacyManagementSOA;encrypt=false;trustServerCertificate=true"
DATABASE_USER="sa"
DATABASE_PASSWORD="your_password"

# JWT secret (tối thiểu 32 bytes, production BẮT BUỘC)
JWT_SECRET="your-secret-key-min-32-bytes-production"

# Application secret
APPLICATION_SECRET="your-application-secret-key"

# Consul (optional nếu chạy standalone)
CONSUL_HOST="localhost"
CONSUL_PORT="8500"

# Dev seeder (default: true ở dev, false ở prod)
SEED_ENABLE_DEV_ADMIN="true"
```

### Tài khoản test (Dev/Test only)

**⚠️ CẢNH BÁO: Mật khẩu này CHỈ dùng cho dev/test, KHÔNG BAO GIỜ dùng production!**

| Username | Password | Role | Branches |
|---|---|---|---|
| admin.demo | Admin@12345 | Quản trị viên (AD) | Tất cả |
| warehouse.demo | Warehouse@12345 | Nhân viên kho (WH) | CN000, CN001 |
| pharmacist.demo | Pharmacist@12345 | Dược sĩ (SP) | CN001 |

## Cách chạy

### 1. Chuẩn bị Database

```sql
-- Chạy 2 scripts theo thứ tự
USE master;
GO
-- 01_create_tables.sql (tạo database và schema)
-- 02_seed_data.sql (dữ liệu mẫu)
```

### 2. Chạy ứng dụng

```bash
# Development mode (port 9001)
sbt run

# Với cấu hình custom
sbt -Dhttp.port=9001 \
    -DDATABASE_URL="..." \
    -DDATABASE_USER="sa" \
    -DDATABASE_PASSWORD="..." \
    -DJWT_SECRET="dev-secret-min-32-bytes-do-not-use-prod" \
    run

# Production mode
sbt -Dplay.server.pidfile.path=/dev/null \
    -Dconfig.file=conf/application.prod.conf \
    'runProd -Dhttp.port=9001'
```

### 3. Kiểm tra Health

```bash
curl http://localhost:9001/health

# Response:
# {
#   "status": "healthy",
#   "service": "auth-service",
#   "instance": "auth-1",
#   "database": "connected",
#   "timestamp": "2024-11-25T15:00:00Z"
# }
```

## Development

### Compile

```bash
sbt compile
```

### Chạy tests

```bash
# Tất cả tests
sbt test

# Chỉ unit tests (không cần database)
sbt "testOnly security.* clients.* seed.*"

# Integration tests (cần SQL Server)
sbt "testOnly * -- -n integration"
```

### Format code

```bash
sbt scalafmtAll
```

## API Endpoints (Phase 1 - Health only)

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/health` | Health check cho Consul (database + service status) |
| GET | `/health/detailed` | Chi tiết system metrics |

**Các endpoint auth/user/role sẽ được implement ở Phase tiếp theo.**

## Kiến trúc

```
auth-service/
├── app/
│   ├── controllers/        # HTTP controllers
│   ├── domain/             # Entities & constants
│   ├── repositories/       # Data access layer (JDBC)
│   │   └── jdbc/           # JDBC implementations
│   ├── security/           # BCrypt, JWT (Phase 2)
│   ├── clients/            # Inter-service clients
│   ├── seed/               # Dev data seeder
│   ├── modules/            # Guice DI modules
│   └── errors/             # Exception handlers (Phase 2)
├── conf/
│   ├── application.conf    # Cấu hình chính
│   ├── routes              # URL routing
│   └── logback.xml         # Logging config
├── test/                   # Unit & integration tests
├── docs/                   # Assumptions & decisions
└── README.md
```

## Repository Layer

- Tất cả repository dùng JDBC thuần + PreparedStatement
- Transaction được quản lý bởi `database.withTransaction()`
- Blocking I/O chạy trên dispatcher riêng (20 threads)
- Username/Email comparison: case-insensitive

## Admin Dev Seeder

- **Tự động chạy** khi Play mode là Dev/Test hoặc `seed.enableDevAdmin=true`
- **Idempotent**: Nếu user đã tồn tại với hash placeholder `REPLACE_WITH_BCRYPT_HASH` thì cập nhật hash thật
- **Production safe**: Không chạy ở Production mode (trừ khi force enable)

## Security

### BCrypt
- Cost factor: 11
- Library: `at.favre.lib:bcrypt:0.10.2`

### JWT (Phase 2)
- Algorithm: HS256
- Access token: 15 phút
- Refresh token: 7 ngày (cố định, không gia hạn)

## Limitations & Known Issues

- **Branch Lookup**: Phase 1 dùng Fake client (in-memory), chỉ support 3 branches cứng
- **Cache**: User status cache là local per-instance, độ trễ tối đa 10 giây
- **Email uniqueness**: Kiểm tra ở application layer, có thể có race condition
- **JWT secret validation**: Chỉ check khi app start, không kiểm tra runtime

## Troubleshooting

### Database connection failed
```
Kiểm tra:
1. SQL Server đang chạy
2. Database "PharmacyManagementSOA" đã tạo
3. User/password đúng
4. Firewall cho phép kết nối port 1433
```

### Admin login không được (Phase 2)
```
Nguyên nhân: Seed data có hash placeholder
Giải pháp: 
- Đảm bảo AdminDevSeeder đã chạy (check log khi start)
- Hoặc manual update hash trong DB
```

### Port 9001 đã được sử dụng
```bash
# Chạy trên port khác
sbt -Dhttp.port=9011 run
```

## Tài liệu khác

- [docs/auth-assumptions.md](docs/auth-assumptions.md) - Giả định và quyết định kỹ thuật
- [yeu-cau-sprint1.md](../yeu-cau-sprint1.md) - Yêu cầu chi tiết Sprint 1

## License

Internal project - Pharmacy Management SOA System
