# PHARMACY MANAGEMENT SOA SYSTEM

## Giới thiệu

Hệ thống quản lý chuỗi nhà thuốc được xây dựng theo kiến trúc SOA (Service-Oriented Architecture), hỗ trợ quản lý tập trung hàng hóa, bán hàng và kho trên nhiều chi nhánh.

**Đề tài:** Sử dụng kiến trúc SOA để phân tích xây dựng và quản lý nhà thuốc

**Nhóm 4 - Đại học Quy Nhơn - Môn Kiến trúc hướng dịch vụ**
---

## Kiến trúc hệ thống

### Tổng quan kiến trúc SOA

```
┌─────────────────────────────────────────────────────────────────┐
│                          CLIENT LAYER                           │
│                    (Web Browser / Mobile App)                   │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                        API GATEWAY                              │
│  - JWT Authentication  - Authorization (RBAC)  - Routing        │
└─────────────────────────────────────────────────────────────────┘
                                │
                    ┌───────────┴───────────┐
                    ▼                       ▼
┌───────────────────────────┐   ┌──────────────────────────┐
│   CONSUL (Service         │   │   SERVICE DISCOVERY      │
│   REGISTRY)               │   │   - Health Checks        │
└───────────────────────────┘   └──────────────────────────┘
                                │
        ┌───────────┬───────────┼───────────┬───────────┐
        ▼           ▼           ▼           ▼           ▼
┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐
│   AUTH    │ │  CATALOG  │ │ INVENTORY │ │ PURCHASE  │ │   SALES   │
│  SERVICE  │ │  SERVICE  │ │  SERVICE  │ │  SERVICE  │ │  SERVICE  │
└───────────┘ └───────────┘ └───────────┘ └───────────┘ └───────────┘
                                │
                                ▼
                    ┌───────────────────────────┐
                    │      REPORT SERVICE       │
                    └───────────────────────────┘
                                │
                                ▼
                    ┌───────────────────────────┐
                    │    DATABASE LAYER         │
                    │  Microsoft SQL Server     │
                    └───────────────────────────┘
```

### Các dịch vụ (Services)

| Dịch vụ | Mô tả | Cổng |
|---------|-------|------|
| **API Gateway** | Điểm vào duy nhất, xác thực JWT, định tuyến | 9000 |
| **Auth Service** | Xác thực, phân quyền, quản lý người dùng | 9001 |
| **Catalog Service** | Quản lý thuốc, danh mục, chi nhánh | 9002 |
| **Inventory Service** | Quản lý kho, lô, tồn kho, chuyển kho | 9003 |
| **Purchase Service** | Nhập hàng, công nợ nhà cung cấp | 9004 |
| **Sales Service** | Bán hàng, hóa đơn, trả hàng | 9005 |
| **Report Service** | Báo cáo, thống kê, dashboard | 9006 |

### Đối tượng sử dụng

| Vai trò | Phạm vi | Nhiệm vụ chính |
|---------|---------|----------------|
| **Admin (AD)** | Toàn chuỗi | Quản lý chi nhánh, tài khoản, phân quyền, báo cáo |
| **Nhân viên kho (WH)** | Chi nhánh được gán | Nhập kho, chuyển kho, kiểm kê, hủy thuốc |
| **Nhân viên bán hàng (SP)** | Chi nhánh được gán | Lập hóa đơn, bán theo đơn, trả hàng |

---

## Công nghệ sử dụng

| Thành phần | Công nghệ |
|------------|-----------|
| **Ngôn ngữ** | Java |
| **Framework** | Play Framework 3.x |
| **Kiến trúc** | SOA (Service-Oriented Architecture) |
| **Giao tiếp** | REST API, HTTP, JSON |
| **Service Registry** | HashiCorp Consul |
| **Xác thực** | JWT (JSON Web Token) |
| **Phân quyền** | RBAC (Role-Based Access Control) |
| **Cơ sở dữ liệu** | Microsoft SQL Server |
| **Frontend** | HTML, CSS, JavaScript, Bootstrap |
| **Build Tool** | SBT |
| **Quản lý mã nguồn** | Git, GitHub |

---

## Cài đặt và triển khai

### Yêu cầu môi trường

- JDK 11+
- SBT 1.9.x+
- SQL Server 2019+
- Consul 1.15.x+
- Docker (tùy chọn)

### Cấu trúc thư mục

```
PharmacyManagementSOA/
├── api-gateway/              # API Gateway Service
├── auth-service/             # Authentication Service
├── catalog-service/          # Catalog Service
├── inventory-service/        # Inventory Service
├── purchase-service/         # Purchase Service
├── sales-service/            # Sales Service
├── report-service/           # Report Service
├── frontend/                 # Frontend Web Application
├── infrastructure/           # Infrastructure Configuration
│   ├── consul/
│   ├── database/
│   └── docker/
├── docs/                     # Documentation
└── scripts/                  # Automation Scripts
```

### Khởi chạy với Docker Compose

```bash
cd infrastructure/docker
docker-compose up -d
```

### Khởi chạy từng dịch vụ

```bash
# Chạy từng service (theo thứ tự bất kỳ)
sbt "project auth-service" run
sbt "project catalog-service" run
sbt "project inventory-service" run
sbt "project purchase-service" run
sbt "project sales-service" run
sbt "project report-service" run

# Chạy API Gateway cuối cùng
sbt "project api-gateway" run
```

### Truy cập ứng dụng

- API Gateway: http://localhost:9000
- Consul UI: http://localhost:8500
- Frontend: http://localhost:8080

---

## API Documentation

### Quy ước API chung

- **Base URL:** `/api/v1/<service>/<resource>`
- **Authentication:** Header `Authorization: Bearer <JWT>`
- **Branch:** Header `X-Branch-Id` (bắt buộc cho API gắn chi nhánh)
- **Phân trang:** `?page=1&size=20&sort=field,desc`
- **Định dạng lỗi:** `{ "code": "ERROR_CODE", "message": "...", "details": {} }`

### Các endpoint chính

#### Auth Service (/api/v1/auth)

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| POST | /auth/login | Đăng nhập, trả JWT |
| POST | /auth/refresh | Làm mới token |
| GET | /auth/me | Thông tin cá nhân |
| GET | /auth/users | Danh sách tài khoản |
| POST | /auth/users | Tạo tài khoản |

#### Catalog Service (/api/v1/catalog)

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | /catalog/drugs | Tìm kiếm thuốc |
| GET | /catalog/drugs/{id} | Chi tiết thuốc |
| POST | /catalog/drugs | Thêm thuốc |
| GET | /catalog/branches | Danh sách chi nhánh |

#### Inventory Service (/api/v1/inventory)

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | /inventory/stock | Tồn kho chi nhánh |
| GET | /inventory/lots | Danh sách lô |
| POST | /inventory/transfers | Lập phiếu chuyển kho |
| POST | /inventory/internal/deduct | Trừ tồn theo FEFO (internal) |

#### Purchase Service (/api/v1/purchase)

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| POST | /purchase/receipts | Lập phiếu nhập |
| GET | /purchase/debts | Công nợ nhà cung cấp |

#### Sales Service (/api/v1/sales)

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| POST | /sales/invoices | Lập hóa đơn |
| GET | /sales/invoices | Tra cứu hóa đơn |
| POST | /sales/returns | Trả hàng |

#### Report Service (/api/v1/reports)

| Endpoint | Mô tả |
|----------|-------|
| /reports/revenue | Báo cáo doanh thu |
| /reports/profit | Báo cáo lợi nhuận |
| /reports/stock | Báo cáo tồn kho |
| /reports/dashboard | Dashboard tổng quan |

---

## Tính năng chính

### Quản lý kho
- Quản lý tồn kho theo lô, hạn dùng, từng chi nhánh
- Tự động chọn lô theo FEFO (First Expire First Out)
- Cảnh báo thuốc sắp hết hạn và dưới mức tồn tối thiểu
- Chuyển kho giữa các chi nhánh

### Bán hàng
- Lập hóa đơn bán lẻ với tích hợp FEFO
- Bán thuốc kê đơn có kiểm soát
- Trả hàng theo hóa đơn gốc
- Quản lý ca làm việc

### Nhập hàng
- Lập phiếu nhập kho
- Quản lý công nợ nhà cung cấp
- Trả hàng nhà cung cấp

### Báo cáo
- Báo cáo doanh thu, lợi nhuận
- Báo cáo tồn kho
- So sánh hiệu quả chi nhánh
- Dashboard tổng quan

---

## Bảo mật

- Xác thực bằng JWT với thời hạn ngắn (15-30 phút)
- Phân quyền RBAC kết hợp phạm vi chi nhánh
- Mật khẩu băm bằng BCrypt
- Chống SQL Injection, XSS
- Ghi nhật ký thao tác nhạy cảm

---

**© 2026 - Nhóm 4 - Đại học Quy Nhơn - Môn Kiến trúc hướng dịch vụ**
