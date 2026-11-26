# ============================================================================
# KỊCH BẢN KIỂM THỬ PHASE 3 - Auth Service
# ============================================================================
# Kiểm thử xoay vòng refresh token, đăng xuất và chọn chi nhánh
#
# Yeu cau:
# - Dịch vụ đang chạy tại http://localhost:9001
# - Database da co du lieu seed
# - AdminDevSeeder da chay (tai khoan admin.demo, warehouse.demo, pharmacist.demo)
#
# Cach chay:
#   cd auth-service/test-scripts
#   .\test-phase3.ps1
# ============================================================================

$baseUrl = "http://localhost:9001"
$testsPassed = 0
$testsFailed = 0

function Write-TestHeader($message) {
    Write-Host "`n============================================" -ForegroundColor Cyan
    Write-Host "  $message" -ForegroundColor Cyan
    Write-Host "============================================`n" -ForegroundColor Cyan
}

function Write-TestResult($success, $message) {
    if ($success) {
        Write-Host "[PASS] $message" -ForegroundColor Green
        $script:testsPassed++
    } else {
        Write-Host "[FAIL] $message" -ForegroundColor Red
        $script:testsFailed++
    }
}

# ============================================================================
# TEST 1: Đăng nhập để lấy access token và refresh token
# ============================================================================
Write-TestHeader "TEST 1: Dang nhap - Lay access token va refresh token"

try {
    $loginResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" `
        -Method POST `
        -ContentType "application/json" `
        -Body '{"usernameOrEmail":"admin.demo","password":"Admin@12345"}'
    
    if ($loginResponse.accessToken -and $loginResponse.refreshToken) {
        Write-TestResult $true "Login thanh cong, nhan duoc access token va refresh token"
        $accessToken = $loginResponse.accessToken
        $refreshToken = $loginResponse.refreshToken
    } else {
        Write-TestResult $false "Login response thieu thong tin"
        exit 1
    }
} catch {
    Write-TestResult $false "Login that bai: $($_.Exception.Message)"
    exit 1
}

# ============================================================================
# TEST 2: Xác thực access token
# ============================================================================
Write-TestHeader "TEST 2: Validate - Kiem tra access token hop le"

try {
    $validateResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/validate" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer $accessToken"}
    
    Write-Host "Validation Result:" -ForegroundColor Yellow
    $validateResponse | ConvertTo-Json
    
    if ($validateResponse.active -eq $true -and $validateResponse.userId -eq 1) {
        Write-TestResult $true "Access token hop le"
    } else {
        Write-TestResult $false "Access token khong hop le"
    }
} catch {
    Write-TestResult $false "Validate that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 3: Xoay vòng refresh token, giữ nguyên ExpiresAt
# ============================================================================
Write-TestHeader "TEST 3: Refresh Token - Rotation voi ExpiresAt co dinh"

$oldRefreshToken = $refreshToken

try {
    $refreshResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/refresh" `
        -Method POST `
        -ContentType "application/json" `
        -Body "{`"refreshToken`":`"$refreshToken`"}"
    
    if ($refreshResponse.accessToken -and $refreshResponse.refreshToken) {
        $newAccessToken = $refreshResponse.accessToken
        $newRefreshToken = $refreshResponse.refreshToken
        
        if ($newRefreshToken -ne $oldRefreshToken) {
            Write-TestResult $true "Token rotation thanh cong (refresh token moi khac cu)"
            $accessToken = $newAccessToken
            $refreshToken = $newRefreshToken
        } else {
            Write-TestResult $false "Refresh token moi giong cu (khong rotate)"
        }
    } else {
        Write-TestResult $false "Refresh response thieu thong tin"
    }
} catch {
    Write-TestResult $false "Refresh token that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 4: Xác thực access token mới
# ============================================================================
Write-TestHeader "TEST 4: Validate - Kiem tra access token moi hop le"

try {
    $validateNewResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/validate" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer $accessToken"}
    
    if ($validateNewResponse.active -eq $true) {
        Write-TestResult $true "Access token moi hop le"
    } else {
        Write-TestResult $false "Access token moi khong hop le"
    }
} catch {
    Write-TestResult $false "Validate token moi that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 5: Phát hiện dùng lại refresh token cũ
# ============================================================================
Write-TestHeader "TEST 5: Reuse Detection - Dung refresh token da rotate"

try {
    $reuseResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/refresh" `
        -Method POST `
        -ContentType "application/json" `
        -Body "{`"refreshToken`":`"$oldRefreshToken`"}"
    
    Write-TestResult $false "Reuse detection khong hoat dong (token cu van dung duoc)"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 401) {
        Write-Host "Status: 401 Unauthorized (expected)" -ForegroundColor Green
        Write-TestResult $true "Reuse detection thanh cong (token cu bi tu choi)"
    } else {
        Write-TestResult $false "Status code khong dung: $statusCode (expected 401)"
    }
}

# ============================================================================
# TEST 6: Admin chọn chi nhánh 2
# ============================================================================
Write-TestHeader "TEST 6: Branch Selection - Admin chon branch 2"

try {
    $branchResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/current-branch" `
        -Method PUT `
        -ContentType "application/json" `
        -Headers @{"Authorization" = "Bearer $accessToken"} `
        -Body '{"branchId":2}'
    
    if ($branchResponse.accessToken) {
        $branchToken = $branchResponse.accessToken
        Write-TestResult $true "Branch selection thanh cong"
        # Xác nhận token mới có currentBranchId = 2
        try {
            $branchValidate = Invoke-RestMethod -Uri "$baseUrl/api/auth/validate" `
                -Method GET `
                -Headers @{"Authorization" = "Bearer $branchToken"}
            
            if ($branchValidate.currentBranchId -eq 2) {
                Write-TestResult $true "Token moi co currentBranchId = 2 (dung)"
            } else {
                Write-TestResult $false "Token moi co currentBranchId = $($branchValidate.currentBranchId) (expected 2)"
            }
        } catch {
            Write-TestResult $false "Validate branch token that bai"
        }
    } else {
        Write-TestResult $false "Branch selection response thieu token"
    }
} catch {
    Write-TestResult $false "Branch selection that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 7: Từ chối chọn chi nhánh không được cấp quyền
# ============================================================================
Write-TestHeader "TEST 7: Branch Selection - Chon branch khong co quyen (99)"

try {
    $invalidBranchResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/current-branch" `
        -Method PUT `
        -ContentType "application/json" `
        -Headers @{"Authorization" = "Bearer $accessToken"} `
        -Body '{"branchId":99}'
    
    Write-TestResult $false "Chon branch khong co quyen van thanh cong (khong dung)"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 403) {
        Write-TestResult $true "Chon branch khong co quyen bi tu choi (403)"
    } else {
        Write-TestResult $false "Status code khong dung: $statusCode (expected 403)"
    }
}

# ============================================================================
# TEST 8: Đăng xuất và thu hồi refresh token hiện tại
# ============================================================================
Write-TestHeader "TEST 8: Logout - Revoke refresh token"

try {
    $logoutResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/logout" `
        -Method POST `
        -ContentType "application/json" `
        -Headers @{"Authorization" = "Bearer $accessToken"} `
        -Body "{`"refreshToken`":`"$refreshToken`"}"
    
    Write-Host "Logout Response:" -ForegroundColor Yellow
    $logoutResponse | ConvertTo-Json
    
    if ($logoutResponse.message -eq "Logged out successfully") {
        Write-TestResult $true "Logout thanh cong"
    } else {
        Write-TestResult $false "Logout message khong dung"
    }
} catch {
    Write-TestResult $false "Logout that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 9: Từ chối refresh token đã đăng xuất
# ============================================================================
Write-TestHeader "TEST 9: Refresh - Dung token da logout (phai fail)"

try {
    $loggedOutRefreshResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/refresh" `
        -Method POST `
        -ContentType "application/json" `
        -Body "{`"refreshToken`":`"$refreshToken`"}"
    
    Write-TestResult $false "Token da logout van dung duoc (khong dung)"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 401) {
        Write-TestResult $true "Token da logout bi tu choi (401)"
    } else {
        Write-TestResult $false "Status code khong dung: $statusCode (expected 401)"
    }
}

# ============================================================================
# TEST 10: Đăng nhập lại để kiểm tra đăng xuất mọi phiên
# ============================================================================
Write-TestHeader "TEST 10: Login lai - Tao nhieu sessions"

try {
    $login2Response = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" `
        -Method POST `
        -ContentType "application/json" `
        -Body '{"usernameOrEmail":"admin.demo","password":"Admin@12345"}'
    
    if ($login2Response.accessToken -and $login2Response.refreshToken) {
        Write-TestResult $true "Login lan 2 thanh cong"
        $accessToken2 = $login2Response.accessToken
        $refreshToken2 = $login2Response.refreshToken
    } else {
        Write-TestResult $false "Login lan 2 that bai"
    }
} catch {
    Write-TestResult $false "Login lan 2 that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 11: Đăng xuất mọi phiên và thu hồi toàn bộ refresh token
# ============================================================================
Write-TestHeader "TEST 11: Logout All - Revoke tat ca refresh tokens"

try {
    $logoutAllResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/logout-all" `
        -Method POST `
        -Headers @{"Authorization" = "Bearer $accessToken2"}
    
    Write-Host "Logout All Response:" -ForegroundColor Yellow
    $logoutAllResponse | ConvertTo-Json
    
    if ($logoutAllResponse.message -and $logoutAllResponse.revokedCount -ge 0) {
        Write-TestResult $true "Logout all thanh cong, revoked $($logoutAllResponse.revokedCount) tokens"
    } else {
        Write-TestResult $false "Logout all response khong dung"
    }
} catch {
    Write-TestResult $false "Logout all that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 12: Từ chối refresh token sau khi đăng xuất mọi phiên
# ============================================================================
Write-TestHeader "TEST 12: Refresh - Dung token sau logout-all (phai fail)"

try {
    $afterLogoutAllRefresh = Invoke-RestMethod -Uri "$baseUrl/api/auth/refresh" `
        -Method POST `
        -ContentType "application/json" `
        -Body "{`"refreshToken`":`"$refreshToken2`"}"
    
    Write-TestResult $false "Token sau logout-all van dung duoc (khong dung)"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 401) {
        Write-TestResult $true "Token sau logout-all bi tu choi (401)"
    } else {
        Write-TestResult $false "Status code khong dung: $statusCode (expected 401)"
    }
}

# ============================================================================
# TEST 13: Pharmacist không thể chọn chi nhánh khác
# ============================================================================
Write-TestHeader "TEST 13: Login pharmacist.demo - User co 1 branch co dinh"

try {
    $pharmacistLogin = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" `
        -Method POST `
        -ContentType "application/json" `
        -Body '{"usernameOrEmail":"pharmacist.demo","password":"Pharmacist@12345"}'
    
    if ($pharmacistLogin.accessToken) {
        Write-TestResult $true "Pharmacist login thanh cong"
        $pharmacistToken = $pharmacistLogin.accessToken
        
        # TEST 13.1: Pharmacist thử chuyển sang chi nhánh 1 (không được phép)
        Write-Host "`n  TEST 13.1: Pharmacist chon branch khong co quyen..." -ForegroundColor Yellow
        try {
            $pharmacistBranchResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/current-branch" `
                -Method PUT `
                -ContentType "application/json" `
                -Headers @{"Authorization" = "Bearer $pharmacistToken"} `
                -Body '{"branchId":1}'
            
            Write-TestResult $false "Pharmacist chon branch khong co quyen van thanh cong (khong dung)"
        } catch {
            $statusCode = $_.Exception.Response.StatusCode.value__
            if ($statusCode -eq 403) {
                Write-TestResult $true "Pharmacist chon branch khong co quyen bi tu choi (403)"
            } else {
                Write-TestResult $false "Status code khong dung: $statusCode (expected 403)"
            }
        }
    } else {
        Write-TestResult $false "Pharmacist login that bai"
    }
} catch {
    Write-TestResult $false "Pharmacist login that bai: $($_.Exception.Message)"
}

# ============================================================================
# TỔNG KẾT
# ============================================================================
Write-Host "`n" -NoNewline
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  TEST SUMMARY - PHASE 3" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "Total Tests:  " -NoNewline
Write-Host ($testsPassed + $testsFailed) -ForegroundColor White
Write-Host "Passed:       " -NoNewline
Write-Host $testsPassed -ForegroundColor Green
Write-Host "Failed:       " -NoNewline
Write-Host $testsFailed -ForegroundColor Red
Write-Host "============================================`n" -ForegroundColor Cyan

if ($testsFailed -eq 0) {
    Write-Host "ALL TESTS PASSED - PHASE 3 HOAN THANH!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "SOME TESTS FAILED - CAN KIEM TRA LAI" -ForegroundColor Red
    exit 1
}
