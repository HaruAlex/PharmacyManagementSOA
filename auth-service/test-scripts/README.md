# Test Scripts - Auth Service

## Mục đích

Các script test tự động cho từng Phase của Auth Service. Dev khác có thể chạy để verify tính năng.

## Yêu cầu

- PowerShell 5.0 trở lên (Windows) hoặc PowerShell Core (cross-platform)
- Auth Service đang chạy trên `http://localhost:9001`
- Database đã có dữ liệu seed (01_create_tables.sql, 02_seed_data.sql)
- AdminDevSeeder đã chạy (tạo tài khoản test)

## Cách chạy

### Phase 2 - Đăng nhập, JWT, Auto-lock

```powershell
cd auth-service/test-scripts
.\test-phase2.ps1
```

**Kết quả mong đợi:**
```
============================================
  TEST SUMMARY - PHASE 2
============================================
Total Tests:  10
Passed:       10
Failed:       0
============================================

✅ ALL TESTS PASSED - PHASE 2 HOÀN THÀNH!
```

## Các test cases trong Phase 2

1. ✅ Đăng nhập thành công với username
2. ✅ Đăng nhập thành công với email
3. ✅ GET /api/auth/me - Lấy thông tin user
4. ✅ GET /api/auth/validate - Token introspection
5. ✅ Đăng nhập sai password 5 lần → auto-lock
6. ✅ Tài khoản đã khóa không đăng nhập được (kể cả đúng password)
7. ✅ Token không hợp lệ bị từ chối (401)
8. ✅ Request thiếu Authorization header bị từ chối (401)
9. ✅ Validate trả về active=false cho token không hợp lệ
10. ✅ JWT chứa đầy đủ claims (sub, role, branch_ids, jti, exp)

## Tài khoản test

| Username | Password | Role | Mục đích |
|---|---|---|---|
| admin.demo | Admin@12345 | AD | Test admin login, permissions |
| warehouse.demo | Warehouse@12345 | WH | Test warehouse role |
| pharmacist.demo | Pharmacist@12345 | SP | Test auto-lock (sẽ bị khóa sau test) |

**Lưu ý:** Tài khoản `pharmacist.demo` sẽ bị khóa sau khi chạy test. Nếu muốn test lại, cần unlock trong database:

```sql
UPDATE auth.Users 
SET IsLocked = 0, FailedLoginCount = 0 
WHERE Username = 'pharmacist.demo';
```

## Troubleshooting

### Lỗi: "Cannot connect to localhost:9001"
```
Giải pháp: Đảm bảo service đang chạy
> sbt run
```

### Lỗi: "Invalid credentials" cho admin.demo
```
Giải pháp: Kiểm tra AdminDevSeeder đã chạy chưa
1. Check log khi start service
2. Query database:
   SELECT Username, PasswordHash FROM auth.Users WHERE Username = 'admin.demo';
3. PasswordHash phải bắt đầu bằng $2a$11$, không phải 'REPLACE_WITH_BCRYPT_HASH'
```

### Lỗi: Test 5 fail (không bị lock sau 5 lần)
```
Giải pháp: Kiểm tra config
1. Xem application.conf: auth.maxFailedAttempts = 5
2. Restart service
```

## Kiểm tra kết quả trong Database

```sql
USE PharmacyManagementSOA;
GO

-- 1. Kiểm tra audit logs
SELECT TOP 20 
    al.AuditLogId,
    u.Username,
    al.ActionCode,
    al.Details,
    al.CreatedAt
FROM auth.AuditLogs al
LEFT JOIN auth.Users u ON al.UserId = u.UserId
ORDER BY al.CreatedAt DESC;

-- 2. Kiểm tra tài khoản bị khóa
SELECT 
    UserId,
    Username,
    IsLocked,
    FailedLoginCount,
    IsActive
FROM auth.Users
WHERE Username IN ('admin.demo', 'warehouse.demo', 'pharmacist.demo');

-- 3. Unlock tài khoản pharmacist.demo để test lại
UPDATE auth.Users 
SET IsLocked = 0, FailedLoginCount = 0 
WHERE Username = 'pharmacist.demo';
```

## Next Phase

- **Phase 3:** Refresh token, logout, chọn chi nhánh (sẽ có test-phase3.ps1)
- **Phase 4:** Quản lý tài khoản (CRUD users)
- **Phase 5:** Hồ sơ cá nhân, đổi mật khẩu
- **Phase 6:** RBAC và Audit log query

## Support

Nếu gặp vấn đề, kiểm tra:
1. Service logs (terminal đang chạy sbt run)
2. Database connection
3. Config trong application.conf
4. Báo cáo issue với log lỗi đầy đủ
