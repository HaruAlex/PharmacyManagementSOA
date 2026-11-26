# ============================================================================
# KỊCH BẢN KIỂM THỬ PHASE 5 - Hồ sơ và đổi mật khẩu (BE-48, BE-49)
# ============================================================================
# Kiểm thử quản lý hồ sơ và đổi mật khẩu
#
# Yeu cau:
# - Dịch vụ đang chạy tại http://localhost:9001
# - Database da co du lieu seed
# - Admin account: admin.demo / Admin@12345
#
# Cach chay:
#   cd auth-service/test-scripts
#   .\test-phase5.ps1
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
# CHUẨN BỊ: Đăng nhập bằng người dùng thường (pharmacist)
# ============================================================================
Write-TestHeader "SETUP: Login as pharmacist.demo"

try {
    $loginResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" `
        -Method POST `
        -ContentType "application/json" `
        -Body '{"usernameOrEmail":"pharmacist.demo","password":"Pharmacist@12345"}'
    
    $userToken = $loginResponse.accessToken
    $script:originalEmail = $loginResponse.user.email
    $script:originalFullName = $loginResponse.user.fullName
    Write-Host "Original: $originalFullName ($originalEmail)" -ForegroundColor Cyan
    Write-TestResult $true "User login thanh cong"
} catch {
    Write-TestResult $false "User login that bai: $($_.Exception.Message)"
    exit 1
}

# ============================================================================
# TEST 1: GET /api/auth/profile - Lay profile
# ============================================================================
Write-TestHeader "TEST 1: GET /api/auth/profile - Lay profile"

try {
    $profile = Invoke-RestMethod -Uri "$baseUrl/api/auth/profile" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer $userToken"}
    
    if ($profile.username -eq "pharmacist.demo" -and 
        $profile.roleCode -eq "SP" -and
        $profile.fullName -ne $null -and
        $profile.email -ne $null) {
        Write-TestResult $true "Get profile thanh cong"
    } else {
        Write-TestResult $false "Profile data khong dung"
    }
} catch {
    Write-TestResult $false "Get profile that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 2: PUT /api/auth/profile - Cap nhat profile hop le
# ============================================================================
Write-TestHeader "TEST 2: PUT /api/auth/profile - Cap nhat profile hop le"

$updateBody = @{
    fullName = "Duoc si cap nhat"
    email = "pharmacist.updated@example.com"
    phone = "0987654321"
} | ConvertTo-Json

try {
    $updated = Invoke-RestMethod -Uri "$baseUrl/api/auth/profile" `
        -Method PUT `
        -ContentType "application/json" `
        -Headers @{"Authorization" = "Bearer $userToken"} `
        -Body $updateBody
    
    if ($updated.fullName -eq "Duoc si cap nhat" -and 
        $updated.email -eq "pharmacist.updated@example.com" -and
        $updated.phone -eq "0987654321" -and
        $updated.username -eq "pharmacist.demo") {
        Write-TestResult $true "Cap nhat profile thanh cong (username khong doi)"
    } else {
        Write-TestResult $false "Cap nhat profile khong dung"
    }
} catch {
    Write-TestResult $false "Cap nhat profile that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 3: PUT /api/auth/profile - Email trung nguoi khac
# ============================================================================
Write-TestHeader "TEST 3: PUT /api/auth/profile - Email trung nguoi khac"

# First, create a test user with a unique email via admin
$adminLogin = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" `
    -Method POST `
    -ContentType "application/json" `
    -Body '{"usernameOrEmail":"admin.demo","password":"Admin@12345"}'

$timestamp = Get-Date -Format "HHmmss"
$testEmail = "testuser_$timestamp@example.com"

$createTestUser = @{
    fullName = "Test User for Email"
    username = "testuser_$timestamp"
    password = "TestUser@123"
    email = $testEmail
    phone = "0123456789"
    roleId = 3
    branchIds = @(1)
} | ConvertTo-Json

try {
    $createdUser = Invoke-RestMethod -Uri "$baseUrl/api/users" `
        -Method POST `
        -ContentType "application/json" `
        -Headers @{"Authorization" = "Bearer $($adminLogin.accessToken)"} `
        -Body $createTestUser
    
    # Now try to update pharmacist email to this test user's email
    $duplicateEmailBody = @{
        fullName = "Test"
        email = $testEmail
        phone = "0123456789"
    } | ConvertTo-Json
    
    $dup = Invoke-RestMethod -Uri "$baseUrl/api/auth/profile" `
        -Method PUT `
        -ContentType "application/json" `
        -Headers @{"Authorization" = "Bearer $userToken"} `
        -Body $duplicateEmailBody
    
    Write-TestResult $false "Email trung van duoc chap nhan (sai)"
    
    # Dọn người dùng kiểm thử
    Invoke-RestMethod -Uri "$baseUrl/api/users/$($createdUser.userId)" `
        -Method DELETE `
        -Headers @{"Authorization" = "Bearer $($adminLogin.accessToken)"} | Out-Null
        
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    
    # Dọn người dùng kiểm thử kể cả khi có lỗi
    try {
        if ($createdUser) {
            Invoke-RestMethod -Uri "$baseUrl/api/users/$($createdUser.userId)" `
                -Method DELETE `
                -Headers @{"Authorization" = "Bearer $($adminLogin.accessToken)"} | Out-Null
        }
    } catch {}
    
    if ($statusCode -eq 409) {
        $errorBody = $_.ErrorDetails.Message | ConvertFrom-Json
        if ($errorBody.code -eq "USER_EMAIL_EXISTS") {
            Write-TestResult $true "Email trung bi tu choi 409 (dung)"
        } else {
            Write-TestResult $false "Error code khong dung: $($errorBody.code)"
        }
    } else {
        Write-TestResult $false "Status code khong dung: $statusCode (expected 409)"
    }
}

# ============================================================================
# TEST 4: PUT /api/auth/profile - Co doi field cam (username, roleId)
# ============================================================================
Write-TestHeader "TEST 4: PUT /api/auth/profile - Co doi field cam"

# Gui request co username va roleId (khong duoc phep doi)
$forbiddenBody = @{
    fullName = "Test Change Forbidden"
    email = "pharmacist.updated@example.com"
    phone = "0123456789"
    username = "hacked"
    roleId = 1
} | ConvertTo-Json

try {
    $result = Invoke-RestMethod -Uri "$baseUrl/api/auth/profile" `
        -Method PUT `
        -ContentType "application/json" `
        -Headers @{"Authorization" = "Bearer $userToken"} `
        -Body $forbiddenBody
    
    # Kiem tra username va roleId khong doi
    if ($result.username -eq "pharmacist.demo" -and $result.roleId -eq 3) {
        Write-TestResult $true "Field cam khong bi doi (username, roleId)"
    } else {
        Write-TestResult $false "Field cam da bi doi (username=$($result.username), roleId=$($result.roleId))"
    }
} catch {
    Write-TestResult $false "Cap nhat that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 5: PUT /api/auth/change-password - Doi mat khau thanh cong
# ============================================================================
Write-TestHeader "TEST 5: PUT /api/auth/change-password - Doi mat khau thanh cong"

$changePassBody = @{
    currentPassword = "Pharmacist@12345"
    newPassword = "NewPharmacist@12345"
    confirmNewPassword = "NewPharmacist@12345"
} | ConvertTo-Json

try {
    $changeResult = Invoke-RestMethod -Uri "$baseUrl/api/auth/change-password" `
        -Method PUT `
        -ContentType "application/json" `
        -Headers @{"Authorization" = "Bearer $userToken"} `
        -Body $changePassBody
    
    Write-TestResult $true "Doi mat khau thanh cong"
    
    # Test login with new password
    $newLogin = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" `
        -Method POST `
        -ContentType "application/json" `
        -Body '{"usernameOrEmail":"pharmacist.demo","password":"NewPharmacist@12345"}'
    
    $script:newUserToken = $newLogin.accessToken
    Write-TestResult $true "Login voi mat khau moi thanh cong"
    
} catch {
    Write-TestResult $false "Doi mat khau that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 6: Xác nhận access token cũ vẫn hoạt động đến khi hết hạn
# ============================================================================
Write-TestHeader "TEST 6: Verify access token cu sau doi mat khau"

Write-Host "Luu y: Access token (15 phut) van hoat dong den het han." -ForegroundColor Yellow
Write-Host "Chi co refresh tokens bi revoke ngay lap tuc." -ForegroundColor Yellow

try {
    # Old access token should still work (until expiry - 15 mins)
    $oldProfile = Invoke-RestMethod -Uri "$baseUrl/api/auth/profile" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer $userToken"}
    
    Write-TestResult $true "Access token cu van hoat dong (thiet ke dung, het han sau 15p)"
} catch {
    Write-TestResult $false "Access token cu khong hoat dong: $($_.Exception.Message)"
}

# ============================================================================
# TEST 7: PUT /api/auth/change-password - Sai mat khau cu
# ============================================================================
Write-TestHeader "TEST 7: PUT /api/auth/change-password - Sai mat khau cu"

$wrongCurrentBody = @{
    currentPassword = "WrongPassword@123"
    newPassword = "NewTest@12345"
    confirmNewPassword = "NewTest@12345"
} | ConvertTo-Json

try {
    $wrongResult = Invoke-RestMethod -Uri "$baseUrl/api/auth/change-password" `
        -Method PUT `
        -ContentType "application/json" `
        -Headers @{"Authorization" = "Bearer $newUserToken"} `
        -Body $wrongCurrentBody
    
    Write-TestResult $false "Sai mat khau cu van duoc chap nhan (sai)"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 401) {
        $errorBody = $_.ErrorDetails.Message | ConvertFrom-Json
        if ($errorBody.code -eq "USER_CURRENT_PASSWORD_INCORRECT") {
            Write-TestResult $true "Sai mat khau cu bi tu choi 401 (dung)"
        } else {
            Write-TestResult $false "Error code khong dung: $($errorBody.code)"
        }
    } else {
        Write-TestResult $false "Status code khong dung: $statusCode (expected 401)"
    }
}

# ============================================================================
# TEST 8: PUT /api/auth/change-password - Mat khau moi trung cu
# ============================================================================
Write-TestHeader "TEST 8: PUT /api/auth/change-password - Mat khau moi trung cu"

$samePassBody = @{
    currentPassword = "NewPharmacist@12345"
    newPassword = "NewPharmacist@12345"
    confirmNewPassword = "NewPharmacist@12345"
} | ConvertTo-Json

try {
    $sameResult = Invoke-RestMethod -Uri "$baseUrl/api/auth/change-password" `
        -Method PUT `
        -ContentType "application/json" `
        -Headers @{"Authorization" = "Bearer $newUserToken"} `
        -Body $samePassBody
    
    Write-TestResult $false "Mat khau moi trung cu van duoc chap nhan (sai)"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 400) {
        $errorBody = $_.ErrorDetails.Message | ConvertFrom-Json
        if ($errorBody.code -eq "USER_NEW_PASSWORD_SAME_AS_OLD") {
            Write-TestResult $true "Mat khau moi trung cu bi tu choi 400 (dung)"
        } else {
            Write-TestResult $false "Error code khong dung: $($errorBody.code)"
        }
    } else {
        Write-TestResult $false "Status code khong dung: $statusCode (expected 400)"
    }
}

# ============================================================================
# TEST 9: PUT /api/auth/change-password - Confirm khong khop
# ============================================================================
Write-TestHeader "TEST 9: PUT /api/auth/change-password - Confirm khong khop"

$mismatchBody = @{
    currentPassword = "NewPharmacist@12345"
    newPassword = "AnotherPass@12345"
    confirmNewPassword = "DifferentPass@12345"
} | ConvertTo-Json

try {
    $mismatchResult = Invoke-RestMethod -Uri "$baseUrl/api/auth/change-password" `
        -Method PUT `
        -ContentType "application/json" `
        -Headers @{"Authorization" = "Bearer $newUserToken"} `
        -Body $mismatchBody
    
    Write-TestResult $false "Confirm khong khop van duoc chap nhan (sai)"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 400) {
        $errorBody = $_.ErrorDetails.Message | ConvertFrom-Json
        if ($errorBody.code -eq "USER_PASSWORD_CONFIRM_MISMATCH") {
            Write-TestResult $true "Confirm khong khop bi tu choi 400 (dung)"
        } else {
            Write-TestResult $false "Error code khong dung: $($errorBody.code)"
        }
    } else {
        Write-TestResult $false "Status code khong dung: $statusCode (expected 400)"
    }
}

# ============================================================================
# TEST 10: PUT /api/auth/change-password - Mat khau yeu
# ============================================================================
Write-TestHeader "TEST 10: PUT /api/auth/change-password - Mat khau yeu"

$weakPassBody = @{
    currentPassword = "NewPharmacist@12345"
    newPassword = "weak"
    confirmNewPassword = "weak"
} | ConvertTo-Json

try {
    $weakResult = Invoke-RestMethod -Uri "$baseUrl/api/auth/change-password" `
        -Method PUT `
        -ContentType "application/json" `
        -Headers @{"Authorization" = "Bearer $newUserToken"} `
        -Body $weakPassBody
    
    Write-TestResult $false "Mat khau yeu van duoc chap nhan (sai)"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 400) {
        $errorBody = $_.ErrorDetails.Message | ConvertFrom-Json
        if ($errorBody.code -eq "PASSWORD_WEAK") {
            Write-TestResult $true "Mat khau yeu bi tu choi 400 (dung)"
        } else {
            Write-TestResult $false "Error code khong dung: $($errorBody.code)"
        }
    } else {
        Write-TestResult $false "Status code khong dung: $statusCode (expected 400)"
    }
}

# ============================================================================
# CLEANUP: Revert password
# ============================================================================
Write-TestHeader "CLEANUP: Revert password to original"

$revertBody = @{
    currentPassword = "NewPharmacist@12345"
    newPassword = "Pharmacist@12345"
    confirmNewPassword = "Pharmacist@12345"
} | ConvertTo-Json

try {
    Invoke-RestMethod -Uri "$baseUrl/api/auth/change-password" `
        -Method PUT `
        -ContentType "application/json" `
        -Headers @{"Authorization" = "Bearer $newUserToken"} `
        -Body $revertBody | Out-Null
    
    Write-Host "Password reverted to original" -ForegroundColor Gray
} catch {
    Write-Host "Failed to revert password: $($_.Exception.Message)" -ForegroundColor Yellow
}

# Revert profile
$revertProfileBody = @{
    fullName = $originalFullName
    email = $originalEmail
    phone = ""
} | ConvertTo-Json

try {
    # Login again with original password
    $finalLogin = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" `
        -Method POST `
        -ContentType "application/json" `
        -Body '{"usernameOrEmail":"pharmacist.demo","password":"Pharmacist@12345"}'
    
    Invoke-RestMethod -Uri "$baseUrl/api/auth/profile" `
        -Method PUT `
        -ContentType "application/json" `
        -Headers @{"Authorization" = "Bearer $($finalLogin.accessToken)"} `
        -Body $revertProfileBody | Out-Null
    
    Write-Host "Profile reverted to original" -ForegroundColor Gray
} catch {
    Write-Host "Failed to revert profile: $($_.Exception.Message)" -ForegroundColor Yellow
}

# ============================================================================
# SUMMARY
# ============================================================================
Write-Host "`n" -NoNewline
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  TEST SUMMARY - PHASE 5" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "Total Tests:  " -NoNewline
Write-Host ($testsPassed + $testsFailed) -ForegroundColor White
Write-Host "Passed:       " -NoNewline
Write-Host $testsPassed -ForegroundColor Green
Write-Host "Failed:       " -NoNewline
Write-Host $testsFailed -ForegroundColor Red
Write-Host "============================================`n" -ForegroundColor Cyan

if ($testsFailed -eq 0) {
    Write-Host "ALL TESTS PASSED - PHASE 5 HOAN THANH!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "SOME TESTS FAILED - CAN KIEM TRA LAI" -ForegroundColor Red
    exit 1
}
