# ============================================================================
# PHASE 4 TEST SCRIPT - User Management (Admin only)
# ============================================================================
# Test CRUD users, lock/unlock, soft delete, reset password
#
# Yeu cau:
# - Service dang chay tren http://localhost:9001
# - Database da co du lieu seed
# - Admin account: admin.demo / Admin@12345
#
# Cach chay:
#   cd auth-service/test-scripts
#   .\test-phase4.ps1
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
# SETUP: Login as admin to get token
# ============================================================================
Write-TestHeader "SETUP: Login as admin"

try {
    $loginResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" `
        -Method POST `
        -ContentType "application/json" `
        -Body '{"usernameOrEmail":"admin.demo","password":"Admin@12345"}'
    
    $adminToken = $loginResponse.accessToken
    Write-Host "Admin Token: $($adminToken.Substring(0, 50))..." -ForegroundColor Cyan
    Write-TestResult $true "Admin login thanh cong"
} catch {
    Write-TestResult $false "Admin login that bai: $($_.Exception.Message)"
    exit 1
}

# ============================================================================
# TEST 1: Tao user moi thanh cong
# ============================================================================
Write-TestHeader "TEST 1: Tao user moi thanh cong"

$newUser = @{
    fullName = "Test User Phase 4"
    username = "testphase4"
    password = "Test@12345"
    email = "testphase4@example.com"
    phone = "0123456789"
    roleId = 3
    branchIds = @(2)
} | ConvertTo-Json

try {
    $createResponse = Invoke-RestMethod -Uri "$baseUrl/api/users" `
        -Method POST `
        -ContentType "application/json" `
        -Headers @{"Authorization" = "Bearer $adminToken"} `
        -Body $newUser
    
    Write-Host "Created User ID: $($createResponse.userId)" -ForegroundColor Yellow
    $script:testUserId = $createResponse.userId
    
    if ($createResponse.username -eq "testphase4" -and 
        $createResponse.fullName -eq "Test User Phase 4" -and
        $createResponse.isActive -eq $true -and
        $createResponse.isLocked -eq $false) {
        Write-TestResult $true "Tao user moi thanh cong"
    } else {
        Write-TestResult $false "User data khong dung"
    }
} catch {
    Write-TestResult $false "Tao user that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 2: Tao user trung username - 409 Conflict
# ============================================================================
Write-TestHeader "TEST 2: Tao user trung username - 409 Conflict"

$duplicateUser = @{
    fullName = "Duplicate User"
    username = "testphase4"
    password = "Test@12345"
    email = "another@example.com"
    phone = "0987654321"
    roleId = 3
    branchIds = @(2)
} | ConvertTo-Json

try {
    $dupResponse = Invoke-RestMethod -Uri "$baseUrl/api/users" `
        -Method POST `
        -ContentType "application/json" `
        -Headers @{"Authorization" = "Bearer $adminToken"} `
        -Body $duplicateUser
    
    Write-TestResult $false "Tao user trung username van thanh cong (sai)"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 409) {
        $errorBody = $_.ErrorDetails.Message | ConvertFrom-Json
        if ($errorBody.code -eq "USER_USERNAME_EXISTS") {
            Write-TestResult $true "Username trung bi tu choi 409 (dung)"
        } else {
            Write-TestResult $false "Error code khong dung: $($errorBody.code)"
        }
    } else {
        Write-TestResult $false "Status code khong dung: $statusCode (expected 409)"
    }
}

# ============================================================================
# TEST 3: Tao user password yeu - 400 Bad Request
# ============================================================================
Write-TestHeader "TEST 3: Tao user password yeu - 400 Bad Request"

$weakPasswordUser = @{
    fullName = "Weak Password User"
    username = "weakuser"
    password = "weak"
    email = "weak@example.com"
    phone = "0123456789"
    roleId = 3
    branchIds = @(2)
} | ConvertTo-Json

try {
    $weakResponse = Invoke-RestMethod -Uri "$baseUrl/api/users" `
        -Method POST `
        -ContentType "application/json" `
        -Headers @{"Authorization" = "Bearer $adminToken"} `
        -Body $weakPasswordUser
    
    Write-TestResult $false "Password yeu van duoc chap nhan (sai)"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 400) {
        $errorBody = $_.ErrorDetails.Message | ConvertFrom-Json
        if ($errorBody.code -eq "PASSWORD_WEAK") {
            Write-TestResult $true "Password yeu bi tu choi 400 (dung)"
        } else {
            Write-TestResult $false "Error code khong dung: $($errorBody.code)"
        }
    } else {
        Write-TestResult $false "Status code khong dung: $statusCode (expected 400)"
    }
}

# ============================================================================
# TEST 4: GET /api/users - List users voi paging
# ============================================================================
Write-TestHeader "TEST 4: GET /api/users - List users voi paging"

try {
    $listResponse = Invoke-RestMethod -Uri "$baseUrl/api/users?page=1&pageSize=10" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer $adminToken"}
    
    Write-Host "Total users: $($listResponse.totalItems)" -ForegroundColor Yellow
    Write-Host "Page: $($listResponse.page), PageSize: $($listResponse.pageSize)" -ForegroundColor Yellow
    
    if ($listResponse.items.Count -gt 0 -and 
        $listResponse.page -eq 1 -and
        $listResponse.pageSize -eq 10 -and
        $listResponse.totalItems -gt 0) {
        Write-TestResult $true "List users thanh cong voi paging"
    } else {
        Write-TestResult $false "List users response khong dung"
    }
} catch {
    Write-TestResult $false "List users that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 5: GET /api/users/:id - Chi tiet user
# ============================================================================
Write-TestHeader "TEST 5: GET /api/users/:id - Chi tiet user"

try {
    $detailResponse = Invoke-RestMethod -Uri "$baseUrl/api/users/$testUserId" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer $adminToken"}
    
    if ($detailResponse.userId -eq $testUserId -and 
        $detailResponse.username -eq "testphase4") {
        Write-TestResult $true "Get user detail thanh cong"
    } else {
        Write-TestResult $false "User detail khong dung"
    }
} catch {
    Write-TestResult $false "Get user detail that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 6: PUT /api/users/:id - Cap nhat user (khong doi username)
# ============================================================================
Write-TestHeader "TEST 6: PUT /api/users/:id - Cap nhat user"

$updateUser = @{
    fullName = "Test User Updated"
    email = "testupdated@example.com"
    phone = "0999999999"
    roleId = 3
} | ConvertTo-Json

try {
    $updateResponse = Invoke-RestMethod -Uri "$baseUrl/api/users/$testUserId" `
        -Method PUT `
        -ContentType "application/json" `
        -Headers @{"Authorization" = "Bearer $adminToken"} `
        -Body $updateUser
    
    if ($updateResponse.fullName -eq "Test User Updated" -and
        $updateResponse.email -eq "testupdated@example.com" -and
        $updateResponse.username -eq "testphase4") {
        Write-TestResult $true "Cap nhat user thanh cong (username khong doi)"
    } else {
        Write-TestResult $false "Cap nhat user khong dung"
    }
} catch {
    Write-TestResult $false "Cap nhat user that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 7: PUT /api/users/:id/role-branches - Gan role + branches
# ============================================================================
Write-TestHeader "TEST 7: PUT /api/users/:id/role-branches - Gan role + branches"

$updateRoleBranches = @{
    roleId = 2
    branchIds = @(1, 2)
} | ConvertTo-Json

try {
    $roleResponse = Invoke-RestMethod -Uri "$baseUrl/api/users/$testUserId/role-branches" `
        -Method PUT `
        -ContentType "application/json" `
        -Headers @{"Authorization" = "Bearer $adminToken"} `
        -Body $updateRoleBranches
    
    if ($roleResponse.roleId -eq 2 -and 
        $roleResponse.roleCode -eq "WH" -and
        $roleResponse.branchIds.Count -eq 2) {
        Write-TestResult $true "Gan role + branches thanh cong"
    } else {
        Write-TestResult $false "Gan role + branches khong dung"
    }
} catch {
    Write-TestResult $false "Gan role + branches that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 8: POST /api/users/:id/lock - Khoa user
# ============================================================================
Write-TestHeader "TEST 8: POST /api/users/:id/lock - Khoa user"

try {
    $lockResponse = Invoke-RestMethod -Uri "$baseUrl/api/users/$testUserId/lock" `
        -Method POST `
        -Headers @{"Authorization" = "Bearer $adminToken"}
    
    if ($lockResponse.message -eq "User locked successfully") {
        Write-TestResult $true "Khoa user thanh cong"
    } else {
        Write-TestResult $false "Lock response khong dung"
    }
} catch {
    Write-TestResult $false "Khoa user that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 9: Verify user bi khoa (kiem tra qua GET)
# ============================================================================
Write-TestHeader "TEST 9: Verify user bi khoa"

try {
    $lockedUser = Invoke-RestMethod -Uri "$baseUrl/api/users/$testUserId" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer $adminToken"}
    
    if ($lockedUser.isLocked -eq $true) {
        Write-TestResult $true "User da bi khoa (isLocked = true)"
    } else {
        Write-TestResult $false "User chua bi khoa (isLocked = false)"
    }
} catch {
    Write-TestResult $false "Verify lock that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 10: POST /api/users/:id/unlock - Mo khoa user
# ============================================================================
Write-TestHeader "TEST 10: POST /api/users/:id/unlock - Mo khoa user"

try {
    $unlockResponse = Invoke-RestMethod -Uri "$baseUrl/api/users/$testUserId/unlock" `
        -Method POST `
        -Headers @{"Authorization" = "Bearer $adminToken"}
    
    if ($unlockResponse.message -eq "User unlocked successfully") {
        Write-TestResult $true "Mo khoa user thanh cong"
    } else {
        Write-TestResult $false "Unlock response khong dung"
    }
} catch {
    Write-TestResult $false "Mo khoa user that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 11: POST /api/users/:id/reset-password - Reset password
# ============================================================================
Write-TestHeader "TEST 11: POST /api/users/:id/reset-password - Reset password"

$resetPassword = @{
    newPassword = "NewPassword@123"
} | ConvertTo-Json

try {
    $resetResponse = Invoke-RestMethod -Uri "$baseUrl/api/users/$testUserId/reset-password" `
        -Method POST `
        -ContentType "application/json" `
        -Headers @{"Authorization" = "Bearer $adminToken"} `
        -Body $resetPassword
    
    if ($resetResponse.message -eq "Password reset successfully") {
        Write-TestResult $true "Reset password thanh cong"
    } else {
        Write-TestResult $false "Reset password response khong dung"
    }
} catch {
    Write-TestResult $false "Reset password that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 12: DELETE /api/users/:id - Xoa mem user
# ============================================================================
Write-TestHeader "TEST 12: DELETE /api/users/:id - Xoa mem user"

try {
    $deleteResponse = Invoke-RestMethod -Uri "$baseUrl/api/users/$testUserId" `
        -Method DELETE `
        -Headers @{"Authorization" = "Bearer $adminToken"}
    
    if ($deleteResponse.message -eq "User deleted successfully") {
        Write-TestResult $true "Xoa mem user thanh cong"
    } else {
        Write-TestResult $false "Delete response khong dung"
    }
} catch {
    Write-TestResult $false "Xoa mem user that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 13: Verify user da bi xoa mem (isActive = false)
# ============================================================================
Write-TestHeader "TEST 13: Verify user da bi xoa mem"

try {
    $deletedUser = Invoke-RestMethod -Uri "$baseUrl/api/users/$testUserId" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer $adminToken"}
    
    if ($deletedUser.isActive -eq $false) {
        Write-TestResult $true "User da bi xoa mem (isActive = false)"
    } else {
        Write-TestResult $false "User chua bi xoa mem (isActive = true)"
    }
} catch {
    Write-TestResult $false "Verify delete that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 14: POST /api/users/:id/activate - Kich hoat lai user
# ============================================================================
Write-TestHeader "TEST 14: POST /api/users/:id/activate - Kich hoat lai user"

try {
    $activateResponse = Invoke-RestMethod -Uri "$baseUrl/api/users/$testUserId/activate" `
        -Method POST `
        -Headers @{"Authorization" = "Bearer $adminToken"}
    
    if ($activateResponse.message -eq "User activated successfully") {
        Write-TestResult $true "Kich hoat lai user thanh cong"
    } else {
        Write-TestResult $false "Activate response khong dung"
    }
} catch {
    Write-TestResult $false "Kich hoat lai user that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 15: Admin tu khoa chinh minh - 403 Forbidden
# ============================================================================
Write-TestHeader "TEST 15: Admin tu khoa chinh minh - 403 Forbidden"

try {
    $selfLockResponse = Invoke-RestMethod -Uri "$baseUrl/api/users/1/lock" `
        -Method POST `
        -Headers @{"Authorization" = "Bearer $adminToken"}
    
    Write-TestResult $false "Admin tu khoa chinh minh van thanh cong (sai)"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 403) {
        $errorBody = $_.ErrorDetails.Message | ConvertFrom-Json
        if ($errorBody.code -eq "CANNOT_LOCK_SELF") {
            Write-TestResult $true "Admin tu khoa bi tu choi 403 (dung)"
        } else {
            Write-TestResult $false "Error code khong dung: $($errorBody.code)"
        }
    } else {
        Write-TestResult $false "Status code khong dung: $statusCode (expected 403)"
    }
}

# ============================================================================
# TEST 16: Admin tu xoa chinh minh - 403 Forbidden
# ============================================================================
Write-TestHeader "TEST 16: Admin tu xoa chinh minh - 403 Forbidden"

try {
    $selfDeleteResponse = Invoke-RestMethod -Uri "$baseUrl/api/users/1" `
        -Method DELETE `
        -Headers @{"Authorization" = "Bearer $adminToken"}
    
    Write-TestResult $false "Admin tu xoa chinh minh van thanh cong (sai)"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 403) {
        $errorBody = $_.ErrorDetails.Message | ConvertFrom-Json
        if ($errorBody.code -eq "CANNOT_DELETE_SELF") {
            Write-TestResult $true "Admin tu xoa bi tu choi 403 (dung)"
        } else {
            Write-TestResult $false "Error code khong dung: $($errorBody.code)"
        }
    } else {
        Write-TestResult $false "Status code khong dung: $statusCode (expected 403)"
    }
}

# ============================================================================
# TEST 17: Non-admin user goi API - 403 Forbidden
# ============================================================================
Write-TestHeader "TEST 17: Non-admin user goi API - 403 Forbidden"

try {
    # Login as pharmacist (role SP, not AD)
    $pharmacistLogin = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" `
        -Method POST `
        -ContentType "application/json" `
        -Body '{"usernameOrEmail":"pharmacist.demo","password":"Pharmacist@12345"}'
    
    $pharmacistToken = $pharmacistLogin.accessToken
    
    # Try to list users
    $nonAdminResponse = Invoke-RestMethod -Uri "$baseUrl/api/users?page=1&pageSize=10" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer $pharmacistToken"}
    
    Write-TestResult $false "Non-admin goi API van thanh cong (sai)"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 403) {
        Write-TestResult $true "Non-admin goi API bi tu choi 403 (dung)"
    } else {
        Write-TestResult $false "Status code khong dung: $statusCode (expected 403)"
    }
}

# ============================================================================
# TEST 18: Filter users by status = "active"
# ============================================================================
Write-TestHeader "TEST 18: Filter users by status = active"

try {
    $activeUsers = Invoke-RestMethod -Uri "$baseUrl/api/users?status=active&page=1&pageSize=100" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer $adminToken"}
    
    $allActive = $true
    foreach ($user in $activeUsers.items) {
        if ($user.isActive -eq $false -or $user.isLocked -eq $true) {
            $allActive = $false
            break
        }
    }
    
    if ($allActive) {
        Write-TestResult $true "Filter status=active thanh cong (tat ca users active va unlocked)"
    } else {
        Write-TestResult $false "Filter status=active khong dung (co user inactive hoac locked)"
    }
} catch {
    Write-TestResult $false "Filter by status that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 19: Filter users by roleId
# ============================================================================
Write-TestHeader "TEST 19: Filter users by roleId = 1 (Admin)"

try {
    $adminUsers = Invoke-RestMethod -Uri "$baseUrl/api/users?roleId=1&page=1&pageSize=100" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer $adminToken"}
    
    $allAdmins = $true
    foreach ($user in $adminUsers.items) {
        if ($user.roleId -ne 1) {
            $allAdmins = $false
            break
        }
    }
    
    if ($allAdmins -and $adminUsers.items.Count -gt 0) {
        Write-TestResult $true "Filter roleId=1 thanh cong (tat ca users la Admin)"
    } else {
        Write-TestResult $false "Filter roleId=1 khong dung"
    }
} catch {
    Write-TestResult $false "Filter by roleId that bai: $($_.Exception.Message)"
}

# ============================================================================
# TEST 20: Sort users by username ASC
# ============================================================================
Write-TestHeader "TEST 20: Sort users by username ASC"

try {
    $sortedUsers = Invoke-RestMethod -Uri "$baseUrl/api/users?sortBy=username&sortOrder=ASC&page=1&pageSize=10" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer $adminToken"}
    
    if ($sortedUsers.items.Count -gt 1) {
        $isSorted = $true
        for ($i = 0; $i -lt ($sortedUsers.items.Count - 1); $i++) {
            if ($sortedUsers.items[$i].username -gt $sortedUsers.items[$i+1].username) {
                $isSorted = $false
                break
            }
        }
        
        if ($isSorted) {
            Write-TestResult $true "Sort by username ASC thanh cong"
        } else {
            Write-TestResult $false "Sort by username ASC khong dung"
        }
    } else {
        Write-TestResult $true "Sort by username ASC (khong du data de test)"
    }
} catch {
    Write-TestResult $false "Sort that bai: $($_.Exception.Message)"
}

# ============================================================================
# CLEANUP: Xoa user test
# ============================================================================
Write-TestHeader "CLEANUP: Xoa user test"

try {
    Invoke-RestMethod -Uri "$baseUrl/api/users/$testUserId" `
        -Method DELETE `
        -Headers @{"Authorization" = "Bearer $adminToken"} | Out-Null
    
    Write-Host "User test da duoc xoa (soft delete)" -ForegroundColor Gray
} catch {
    Write-Host "Khong the xoa user test: $($_.Exception.Message)" -ForegroundColor Yellow
}

# ============================================================================
# SUMMARY
# ============================================================================
Write-Host "`n" -NoNewline
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  TEST SUMMARY - PHASE 4" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "Total Tests:  " -NoNewline
Write-Host ($testsPassed + $testsFailed) -ForegroundColor White
Write-Host "Passed:       " -NoNewline
Write-Host $testsPassed -ForegroundColor Green
Write-Host "Failed:       " -NoNewline
Write-Host $testsFailed -ForegroundColor Red
Write-Host "============================================`n" -ForegroundColor Cyan

if ($testsFailed -eq 0) {
    Write-Host "ALL TESTS PASSED - PHASE 4 HOAN THANH!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "SOME TESTS FAILED - CAN KIEM TRA LAI" -ForegroundColor Red
    exit 1
}
