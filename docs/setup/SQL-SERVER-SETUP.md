# SQL Server Setup Guide

## Yêu cầu
- Windows 10+ hoặc Linux
- Docker (nếu chọn Docker option)
- Hoặc: SQL Server Express 2019+ installed locally

---

## Option 1: Docker (Khuyên dùng, nhanh nhất)

### 1. Cài Docker Desktop
[Download](https://www.docker.com/products/docker-desktop)

### 2. Khởi động SQL Server container

```powershell
docker run -e "ACCEPT_EULA=Y" -e "SA_PASSWORD=123456" `
  -p 1433:1433 `
  --name sqlserver `
  -d mcr.microsoft.com/mssql/server:2019-latest
```

### 3. Kiểm tra container chạy

```powershell
docker ps | Select-String sqlserver
```

Output:
```
CONTAINER ID  IMAGE                      COMMAND              STATUS         PORTS
a1b2c3d4e5f6  mcr.microsoft.com/...     "/opt/mssql/bin..."  Up 2 minutes   0.0.0.0:1433->1433/tcp
```

### 4. Kết nối test

```powershell
sqlcmd -S localhost -U sa -P "123456" -Q "SELECT @@VERSION"
```

Nếu thấy version SQL Server, kết nối thành công!

### 5. Tắt khi không dùng

```powershell
docker stop sqlserver
docker rm sqlserver  # (nếu muốn xóa)
```

---

## Option 2: SQL Server Express (Local install)

### 1. Download
[SQL Server 2019 Express](https://www.microsoft.com/en-us/sql-server/sql-server-editions-express)

### 2. Cài đặt
- Chọn "Basic"
- Chọn path cài đặt
- Accept defaults

### 3. Khởi động dịch vụ
```powershell
# PowerShell as Administrator
Start-Service MSSQLSERVER

# Kiểm tra
Get-Service MSSQLSERVER | Select-Object Status
```

### 4. Kết nối test
```powershell
sqlcmd -S localhost -U sa -P "123456" -Q "SELECT @@VERSION"
```

---

## Tạo Database PharmacyManagementSOA

Sau khi SQL Server chạy, chạy script tạo database:

```powershell
cd d:\Java\PharmacyManagementSOA
sqlcmd -S localhost -U sa -P "123456" -i docs/database/01_create_tables.sql
sqlcmd -S localhost -U sa -P "123456" -i docs/database/02_seed_data.sql
```

Hoặc sử dụng SQL Server Management Studio (SSMS) để chạy các file SQL.

---

## Kiểm chứng Database

```powershell
sqlcmd -S localhost -U sa -P "123456" -Q "SELECT name FROM sys.databases WHERE name='PharmacyManagementSOA'"
```

Output:
```
name
PharmacyManagementSOA
```

---

## Connection String

Các service sẽ dùng connection string này (trong `application.conf`):

```
jdbc:sqlserver://localhost:1433;databaseName=PharmacyManagementSOA;encrypt=false
```

**User**: `sa`
**Password**: `123456`

---

## Troubleshooting

### Lỗi: Connection refused
- Kiểm tra SQL Server chạy: `docker ps` (Docker) hoặc `Get-Service MSSQLSERVER` (Local)
- Kiểm tra firewall allow port 1433

### Lỗi: Login failed for user 'sa'
- Kiểm tra password đúng: `123456`
- Kiểm tra user SA được enable

### Lỗi: Database not found
- Chạy script tạo database: `01_create_tables.sql`

---

**Một khi SQL Server chạy, các service Play Framework sẽ kết nối tự động!**
