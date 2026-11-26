# ============================================================================
# KỊCH BẢN KIỂM THỬ PHASE 2 - Auth Service
# ============================================================================
# Test đăng nhập, JWT, khóa tài khoản tự động
#
# Yêu cầu:
# - Dịch vụ đang chạy tại http://localhost:9001
# - Database đã có dữ liệu seed
# - AdminDevSeeder đã chạy (tài khoản admin.demo, warehouse.demo, pharmacist.demo)
#
# Cách chạy:
#   cd auth-service/test-scripts
#   .\test-phase2.ps1
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
# TEST 1: Đăng nhập thành công với username
# ============================================================================
Write-TestHeader "TEST 1: Đăng nhập thành công với username"

try {
    $loginResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" `
        -Method POST `
        -ContentType "application/json" `
        -Body '{"usernameOrEmail":"admin.demo","password":"Admin@12345"}'
    
    if ($loginResponse.accessToken -and $loginResponse.user.userId -eq 1) {
        Write-TestResult $true "Login với username thành công"
        $token = $loginResponse.accessToken
    } else {
        Write-TestResult $false "Login response thiếu thông tin"
    }
} catch {
    Write-TestResult $false "Login với username thất bại: $($_.Exception.Message)"
}

# ============================================================================
# TEST 2: Đăng nhập thành công với email
# ============================================================================
Write-TestHeader "TEST 2: Đăng nhập thành công với email"

try {
    $emailLoginResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" `
        -Method POST `
        -ContentType "application/json" `
        -Body '{"usernameOrEmail":"admin@example.local","password":"Admin@12345"}'
    
    if ($emailLoginResponse.accessToken -and $emailLoginResponse.user.username -eq "admin.demo") {
        Write-TestResult $true "Login với email thành công"
    } else {
        Write-TestResult $false "Login với email không đúng"
    }
} catch {
    Write-TestResult $false "Login với email thất bại: $($_.Exception.Message)"
}

# ============================================================================
# TEST 3: GET /api/auth/me - Lấy thông tin user hiện tại
# ============================================================================
Write-TestHeader "TEST 3: GET /api/auth/me"

try {
    $meResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/me" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer $token"}
    
    Write-Host "User Info:" -ForegroundColor Yellow
    $meResponse | ConvertTo-Json -Depth 10
    
    if ($meResponse.userId -eq 1 -and $meResponse.username -eq "admin.demo") {
        Write-TestResult $true "GET /api/auth/me thành công"
    } else {
        Write-TestResult $false "GET /api/auth/me dữ liệu không đúng"
    }
} catch {
    Write-TestResult $false "GET /api/auth/me thất bại: $($_.Exception.Message)"
}

# ============================================================================
# TEST 4: GET /api/auth/validate - Kiểm tra trạng thái token
# ============================================================================
Write-TestHeader "TEST 4: GET /api/auth/validate"

try {
    $validateResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/validate" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer $token"}
    
    Write-Host "Validation Result:" -ForegroundColor Yellow
    $validateResponse | ConvertTo-Json
    
    if ($validateResponse.active -eq $true -and $validateResponse.userId -eq 1) {
        Write-TestResult $true "Token validation thành công"
    } else {
        Write-TestResult $false "Token validation không đúng"
    }
} catch {
    Write-TestResult $false "Token validation thất bại: $($_.Exception.Message)"
}

# ============================================================================
# TEST 5: Đăng nhập sai password - 5 lần để bị khóa
# ============================================================================
Write-TestHeader "TEST 5: Đăng nhập sai password 5 lần (auto-lock)"

$failCount = 0
for ($i = 1; $i -le 5; $i++) {
    Write-Host "`nLần $i/5:" -ForegroundColor Yellow
    try {
        $badLogin = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" `
            -Method POST `
            -ContentType "application/json" `
            -Body "{`"usernameOrEmail`":`"pharmacist.demo`",`"password`":`"wrong$i`"}"
        
        Write-Host "  Unexpected: Login succeeded (should fail)" -ForegroundColor Red
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        $errorBody = $_.ErrorDetails.Message | ConvertFrom-Json
        
        if ($i -lt 5) {
            if ($statusCode -eq 401) {
                Write-Host "  [OK] Status: 401 Unauthorized (expected)" -ForegroundColor Green
                Write-Host "  Error: $($errorBody.code) - $($errorBody.message)" -ForegroundColor Gray
                $failCount++
            } else {
                Write-Host "  [FAIL] Status: $statusCode (expected 401)" -ForegroundColor Red
            }
        } else {
            # Lần thứ 5 nên bị lock (423)
            if ($statusCode -eq 423) {
                Write-Host "  [OK] Status: 423 Locked (account auto-locked)" -ForegroundColor Green
                Write-Host "  Error: $($errorBody.code) - $($errorBody.message)" -ForegroundColor Gray
                $failCount++
            } else {
                Write-Host "  [FAIL] Status: $statusCode (expected 423)" -ForegroundColor Red
            }
        }
    }
}

if ($failCount -eq 5) {
    Write-TestResult $true "Tài khoản bị khóa sau 5 lần nhập sai"
} else {
    Write-TestResult $false "Cơ chế khóa tài khoản không hoạt động đúng"
}

# ============================================================================
# TEST 6: Đăng nhập đúng password nhưng tài khoản đã bị khóa
# ============================================================================
Write-TestHeader "TEST 6: Đăng nhập đúng password nhưng tài khoản đã khóa"

try {
    $lockedLogin = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" `
        -Method POST `
        -ContentType "application/json" `
        -Body '{"usernameOrEmail":"pharmacist.demo","password":"Pharmacist@12345"}'
    
    Write-TestResult $false "Tài khoản đã khóa vẫn đăng nhập được (không đúng)"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    $errorBody = $_.ErrorDetails.Message | ConvertFrom-Json
    
    if ($statusCode -eq 423) {
        Write-Host "Status: 423 Locked" -ForegroundColor Green
        Write-Host "Error: $($errorBody.code) - $($errorBody.message)" -ForegroundColor Gray
        Write-TestResult $true "Tài khoản đã khóa không thể đăng nhập (đúng)"
    } else {
        Write-TestResult $false "Status code không đúng: $statusCode (expected 423)"
    }
}

# ============================================================================
# TEST 7: Token không hợp lệ
# ============================================================================
Write-TestHeader "TEST 7: Token không hợp lệ"

try {
    $invalidTokenResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/me" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer invalid_token_xyz"}
    
    Write-TestResult $false "Token không hợp lệ vẫn được chấp nhận (không đúng)"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 401) {
        Write-TestResult $true "Token không hợp lệ bị từ chối (đúng)"
    } else {
        Write-TestResult $false "Status code không đúng: $statusCode (expected 401)"
    }
}

# ============================================================================
# TEST 8: Thiếu header Authorization
# ============================================================================
Write-TestHeader "TEST 8: Thiếu Authorization header"

try {
    $noAuthResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/me" -Method GET
    Write-TestResult $false "Request không có token vẫn thành công (không đúng)"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 401) {
        Write-TestResult $true "Request thiếu token bị từ chối (đúng)"
    } else {
        Write-TestResult $false "Status code không đúng: $statusCode (expected 401)"
    }
}

# ============================================================================
# TEST 9: Xác thực token không hợp lệ
# ============================================================================
Write-TestHeader "TEST 9: Validate token không hợp lệ"

try {
    $validateBadResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/validate" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer invalid_token"}
    
    if ($validateBadResponse.active -eq $false) {
        Write-TestResult $true "Validate trả về active=false cho token không hợp lệ"
    } else {
        Write-TestResult $false "Validate trả về active=true cho token không hợp lệ"
    }
} catch {
    # Endpoint validate không ném ngoại lệ, trả về {active: false}
    Write-TestResult $false "Validate endpoint throw exception (không đúng thiết kế)"
}

# ============================================================================
# TEST 10: Kiểm tra JWT claims
# ============================================================================
Write-TestHeader "TEST 10: Kiểm tra JWT token structure"

try {
    # Giải mã JWT token (base64)
    $tokenParts = $token.Split('.')
    if ($tokenParts.Length -eq 3) {
        # Giải mã payload (phần thứ hai)
        $payloadBase64 = $tokenParts[1]
        # Thêm padding nếu cần
        while ($payloadBase64.Length % 4 -ne 0) {
            $payloadBase64 += "="
        }
        $payloadBytes = [System.Convert]::FromBase64String($payloadBase64)
        $payloadJson = [System.Text.Encoding]::UTF8.GetString($payloadBytes)
        $payload = $payloadJson | ConvertFrom-Json
        
        Write-Host "JWT Payload:" -ForegroundColor Yellow
        $payload | ConvertTo-Json
        
        $hasRequiredClaims = $payload.sub -and $payload.role -and $payload.branch_ids -and $payload.jti -and $payload.exp
        
        if ($hasRequiredClaims) {
            Write-TestResult $true "JWT chứa đầy đủ claims (sub, role, branch_ids, jti, exp)"
        } else {
            Write-TestResult $false "JWT thiếu claims bắt buộc"
        }
    } else {
        Write-TestResult $false "JWT format không đúng"
    }
} catch {
    Write-TestResult $false "Không thể decode JWT: $($_.Exception.Message)"
}

# ============================================================================
# TỔNG KẾT
# ============================================================================
# Khôi phục tài khoản pharmacist.demo sau bài kiểm thử khóa tự động.
try {
    $users = Invoke-RestMethod -Uri "$baseUrl/api/users?name=pharmacist.demo&page=1&pageSize=100" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer $token"}
    $pharmacist = $users.items | Where-Object { $_.username -eq "pharmacist.demo" } | Select-Object -First 1
    if ($pharmacist) {
        Invoke-RestMethod -Uri "$baseUrl/api/users/$($pharmacist.userId)/unlock" `
            -Method POST `
            -Headers @{"Authorization" = "Bearer $token"} | Out-Null
        Write-Host "Đã mở khóa pharmacist.demo sau kiểm thử." -ForegroundColor Gray
    } else {
        Write-Host "Không tìm thấy pharmacist.demo để khôi phục trạng thái." -ForegroundColor Yellow
        $testsFailed++
    }
} catch {
    Write-Host "Không thể khôi phục pharmacist.demo: $($_.Exception.Message)" -ForegroundColor Yellow
    $testsFailed++
}

Write-Host "`n" -NoNewline
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  TEST SUMMARY - PHASE 2" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "Total Tests:  " -NoNewline
Write-Host ($testsPassed + $testsFailed) -ForegroundColor White
Write-Host "Passed:       " -NoNewline
Write-Host $testsPassed -ForegroundColor Green
Write-Host "Failed:       " -NoNewline
Write-Host $testsFailed -ForegroundColor Red
Write-Host "============================================`n" -ForegroundColor Cyan

if ($testsFailed -eq 0) {
    Write-Host "✅ ALL TESTS PASSED - PHASE 2 HOÀN THÀNH!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "❌ SOME TESTS FAILED - CẦN KIỂM TRA LẠI" -ForegroundColor Red
    exit 1
}
