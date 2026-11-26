# Kiểm thử API RBAC và nhật ký kiểm toán Phase 6
# Nội dung: CRUD vai trò/quyền, ánh xạ quyền-vai trò, tìm kiếm nhật ký và kiểm soát truy cập

$baseUrl = "http://localhost:9001/api"
$runId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$roleCode = "T$runId"
$permissionCode = "P$runId"
$testsPassed = 0
$testsFailed = 0

function Test-Endpoint {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers = @{},
        [object]$Body = $null,
        [int]$ExpectedStatus,
        [scriptblock]$Validation = $null
    )
    
    Write-Host "`n=== Test: $Name ===" -ForegroundColor Cyan
    
    try {
        $params = @{
            Uri = $Url
            Method = $Method
            Headers = $Headers
            ContentType = "application/json"
        }
        
        if ($Body -ne $null) {
            $params.Body = ($Body | ConvertTo-Json -Depth 10)
        }
        
        try {
            $response = Invoke-WebRequest @params -UseBasicParsing
            $statusCode = $response.StatusCode
            $content = $response.Content | ConvertFrom-Json
        } catch {
            if ($_.Exception.Response) {
                $statusCode = $_.Exception.Response.StatusCode.value__
                $errorBody = $_.ErrorDetails.Message
                if (-not $errorBody) {
                    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                    $errorBody = $reader.ReadToEnd()
                    $reader.Close()
                }
                if ($errorBody) {
                    $content = $errorBody | ConvertFrom-Json
                }
            } else {
                throw
            }
        }
        
        Write-Host "Status: $statusCode"
        if ($statusCode -eq $ExpectedStatus) {
            if ($Validation -eq $null -or (& $Validation $content)) {
                Write-Host "PASS" -ForegroundColor Green
                return @{
                    Success = $true
                    Data = $content
                }
            } else {
                Write-Host "FAIL: Validation failed" -ForegroundColor Red
                return @{ Success = $false }
            }
        } else {
            Write-Host "FAIL: Expected status $ExpectedStatus, got $statusCode" -ForegroundColor Red
            return @{ Success = $false }
        }
    } catch {
        Write-Host "FAIL: $_" -ForegroundColor Red
        return @{ Success = $false }
    }
}

Write-Host "==================================================" -ForegroundColor Yellow
Write-Host "   Phase 6: RBAC and Audit Log API Tests" -ForegroundColor Yellow
Write-Host "==================================================" -ForegroundColor Yellow

# Bước 1: Admin đăng nhập
Write-Host "`n--- Step 1: Admin Login ---" -ForegroundColor Magenta
$loginResult = Test-Endpoint `
    -Name "Admin login" `
    -Method "POST" `
    -Url "$baseUrl/auth/login" `
    -Body @{
        usernameOrEmail = "admin.demo"
        password = "Admin@12345"
    } `
    -ExpectedStatus 200 `
    -Validation { param($r) $r.accessToken -and $r.refreshToken -and $r.user.username -eq "admin.demo" }

if (-not $loginResult.Success) {
    Write-Host "`nCannot proceed without admin token" -ForegroundColor Red
    exit 1
}
$testsPassed++

$adminToken = $loginResult.Data.accessToken
$adminUserId = $loginResult.Data.user.userId
$adminHeaders = @{ "Authorization" = "Bearer $adminToken" }

# Bước 2: Tạo vai trò mới
Write-Host "`n--- Step 2: Create Role ---" -ForegroundColor Magenta
$createRoleResult = Test-Endpoint `
    -Name "Create a uniquely named role" `
    -Method "POST" `
    -Url "$baseUrl/roles" `
    -Headers $adminHeaders `
    -Body @{
        code = $roleCode
        name = "Branch Manager"
        isActive = $true
    } `
    -ExpectedStatus 201 `
    -Validation { param($r) $r.roleId -and $r.roleCode -eq $roleCode -and $r.roleName -eq "Branch Manager" }

if ($createRoleResult.Success) {
    $testsPassed++
    $newRoleId = $createRoleResult.Data.roleId
} else {
    $testsFailed++
    $newRoleId = $null
}

# Bước 3: Lấy danh sách vai trò
Write-Host "`n--- Step 3: List Roles ---" -ForegroundColor Magenta
$listRolesResult = Test-Endpoint `
    -Name "List all roles" `
    -Method "GET" `
    -Url "$baseUrl/roles" `
    -Headers $adminHeaders `
    -ExpectedStatus 200 `
    -Validation { param($r) $r.Count -ge 2 }  # At least AD and BM

if ($listRolesResult.Success) { $testsPassed++ } else { $testsFailed++ }

# Bước 4: Lấy vai trò theo ID
Write-Host "`n--- Step 4: Get Role by ID ---" -ForegroundColor Magenta
if ($newRoleId) {
    $getRoleResult = Test-Endpoint `
        -Name "Get role BM by ID" `
        -Method "GET" `
        -Url "$baseUrl/roles/$newRoleId" `
        -Headers $adminHeaders `
        -ExpectedStatus 200 `
        -Validation { param($r) $r.roleId -eq $newRoleId -and $r.roleCode -eq $roleCode }
    
    if ($getRoleResult.Success) { $testsPassed++ } else { $testsFailed++ }
} else {
    Write-Host "Skipping test (no role ID)" -ForegroundColor Yellow
    $testsFailed++
}

# Bước 5: Cập nhật vai trò
Write-Host "`n--- Step 5: Update Role ---" -ForegroundColor Magenta
if ($newRoleId) {
    $updateRoleResult = Test-Endpoint `
        -Name "Update role name" `
        -Method "PUT" `
        -Url "$baseUrl/roles/$newRoleId" `
        -Headers $adminHeaders `
        -Body @{
            name = "Branch Operations Manager"
            isActive = $true
        } `
        -ExpectedStatus 200 `
        -Validation { param($r) $r.roleName -eq "Branch Operations Manager" }
    
    if ($updateRoleResult.Success) { $testsPassed++ } else { $testsFailed++ }
} else {
    Write-Host "Skipping test (no role ID)" -ForegroundColor Yellow
    $testsFailed++
}

# Bước 6: Tạo quyền mới
Write-Host "`n--- Step 6: Create Permission ---" -ForegroundColor Magenta
$createPermResult = Test-Endpoint `
    -Name "Create a uniquely named permission" `
    -Method "POST" `
    -Url "$baseUrl/permissions" `
    -Headers $adminHeaders `
    -Body @{
        code = $permissionCode
        name = "View Branch"
    } `
    -ExpectedStatus 201 `
    -Validation { param($r) $r.permissionId -and $r.permissionCode -eq $permissionCode }

if ($createPermResult.Success) {
    $testsPassed++
    $newPermId = $createPermResult.Data.permissionId
} else {
    $testsFailed++
    $newPermId = $null
}

# Bước 7: Lấy danh sách quyền
Write-Host "`n--- Step 7: List Permissions ---" -ForegroundColor Magenta
$listPermsResult = Test-Endpoint `
    -Name "List all permissions" `
    -Method "GET" `
    -Url "$baseUrl/permissions" `
    -Headers $adminHeaders `
    -ExpectedStatus 200 `
    -Validation { param($r) $r.Count -ge 1 }

if ($listPermsResult.Success) { $testsPassed++ } else { $testsFailed++ }

# Bước 8: Lấy quyền theo ID
Write-Host "`n--- Step 8: Get Permission by ID ---" -ForegroundColor Magenta
if ($newPermId) {
    $getPermResult = Test-Endpoint `
        -Name "Get created permission by ID" `
        -Method "GET" `
        -Url "$baseUrl/permissions/$newPermId" `
        -Headers $adminHeaders `
        -ExpectedStatus 200 `
        -Validation { param($r) $r.permissionId -eq $newPermId -and $r.permissionCode -eq $permissionCode }
    
    if ($getPermResult.Success) { $testsPassed++ } else { $testsFailed++ }
} else {
    Write-Host "Skipping test (no permission ID)" -ForegroundColor Yellow
    $testsFailed++
}

# Bước 9: Cập nhật quyền
Write-Host "`n--- Step 9: Update Permission ---" -ForegroundColor Magenta
if ($newPermId) {
    $updatePermResult = Test-Endpoint `
        -Name "Update permission name" `
        -Method "PUT" `
        -Url "$baseUrl/permissions/$newPermId" `
        -Headers $adminHeaders `
        -Body @{
            name = "View Branch Details"
        } `
        -ExpectedStatus 200 `
        -Validation { param($r) $r.permissionName -eq "View Branch Details" }
    
    if ($updatePermResult.Success) { $testsPassed++ } else { $testsFailed++ }
} else {
    Write-Host "Skipping test (no permission ID)" -ForegroundColor Yellow
    $testsFailed++
}

# Bước 10: Lấy ID của quyền USER_MANAGE
Write-Host "`n--- Step 10: Get USER_MANAGE Permission ---" -ForegroundColor Magenta
if ($listPermsResult.Success) {
    $userManagePerm = $listPermsResult.Data | Where-Object { $_.permissionCode -eq "USER_MANAGE" }
    if ($userManagePerm) {
        $userManagePermId = $userManagePerm.permissionId
        Write-Host "Found USER_MANAGE permission: ID=$userManagePermId" -ForegroundColor Green
        $testsPassed++
    } else {
        Write-Host "USER_MANAGE permission not found" -ForegroundColor Red
        $testsFailed++
        $userManagePermId = $null
    }
} else {
    Write-Host "Skipping test (no permissions list)" -ForegroundColor Yellow
    $testsFailed++
    $userManagePermId = $null
}

# Bước 11: Gán quyền cho vai trò mới
Write-Host "`n--- Step 11: Assign Permissions to Role ---" -ForegroundColor Magenta
if ($newRoleId -and $newPermId) {
    $assignPermsResult = Test-Endpoint `
        -Name "Assign created permission to role" `
        -Method "PUT" `
        -Url "$baseUrl/roles/$newRoleId/permissions" `
        -Headers $adminHeaders `
        -Body @{
            permissionIds = @($newPermId)
        } `
        -ExpectedStatus 200 `
        -Validation { param($r) $r.Count -eq 1 -and $r[0].permissionCode -eq $permissionCode }
    
    if ($assignPermsResult.Success) { $testsPassed++ } else { $testsFailed++ }
} else {
    Write-Host "Skipping test (no role or permission ID)" -ForegroundColor Yellow
    $testsFailed++
}

# Bước 12: Lấy quyền của vai trò
Write-Host "`n--- Step 12: Get Role Permissions ---" -ForegroundColor Magenta
if ($newRoleId) {
    $getRolePermsResult = Test-Endpoint `
        -Name "Get permissions for created role" `
        -Method "GET" `
        -Url "$baseUrl/roles/$newRoleId/permissions" `
        -Headers $adminHeaders `
        -ExpectedStatus 200 `
        -Validation { param($r) $r.Count -eq 1 -and $r[0].permissionCode -eq $permissionCode }
    
    if ($getRolePermsResult.Success) { $testsPassed++ } else { $testsFailed++ }
} else {
    Write-Host "Skipping test (no role ID)" -ForegroundColor Yellow
    $testsFailed++
}

# Bước 13: Thử gỡ USER_MANAGE khỏi vai trò AD (phải bị từ chối)
Write-Host "`n--- Step 13: Protect AD Role ---" -ForegroundColor Magenta
# Lấy ID vai trò AD
$adRole = $listRolesResult.Data | Where-Object { $_.roleCode -eq "AD" }
if ($adRole) {
    $adRoleId = $adRole.roleId
    $protectADResult = Test-Endpoint `
        -Name "Try to remove USER_MANAGE from AD role (should fail)" `
        -Method "PUT" `
        -Url "$baseUrl/roles/$adRoleId/permissions" `
        -Headers $adminHeaders `
        -Body @{
            permissionIds = @()  # Empty array
        } `
        -ExpectedStatus 403 `
        -Validation { param($r) $r.code -eq "PERMISSION_CANNOT_REMOVE_FROM_ADMIN" }
    
    if ($protectADResult.Success) { $testsPassed++ } else { $testsFailed++ }
} else {
    Write-Host "AD role not found" -ForegroundColor Red
    $testsFailed++
}

# Bước 14: Thử vô hiệu hóa vai trò đang có người dùng hoạt động (phải bị từ chối)
Write-Host "`n--- Step 14: Prevent Deactivating Role with Active Users ---" -ForegroundColor Magenta
if ($adRole) {
    $deactivateRoleResult = Test-Endpoint `
        -Name "Try to deactivate AD role (should fail - has active users)" `
        -Method "PUT" `
        -Url "$baseUrl/roles/$adRoleId" `
        -Headers $adminHeaders `
        -Body @{
            name = "Administrator"
            isActive = $false
        } `
        -ExpectedStatus 409 `
        -Validation { param($r) $r.code -eq "ROLE_IN_USE" }
    
    if ($deactivateRoleResult.Success) { $testsPassed++ } else { $testsFailed++ }
} else {
    Write-Host "Skipping test (no AD role)" -ForegroundColor Yellow
    $testsFailed++
}

# Bước 15: Tìm kiếm nhật ký kiểm toán
Write-Host "`n--- Step 15: Search Audit Logs ---" -ForegroundColor Magenta
$searchAuditResult = Test-Endpoint `
    -Name "Search audit logs (no filter)" `
    -Method "GET" `
    -Url "$baseUrl/audit-logs?page=1&pageSize=20" `
    -Headers $adminHeaders `
    -ExpectedStatus 200 `
    -Validation { param($r) $null -ne $r.items -and $r.totalItems -ge 0 }

if ($searchAuditResult.Success) { $testsPassed++ } else { $testsFailed++ }

# Bước 16: Tìm nhật ký kiểm toán theo hành động
Write-Host "`n--- Step 16: Search Audit by Action ---" -ForegroundColor Magenta
$searchByActionResult = Test-Endpoint `
    -Name "Search audit logs for LOGIN action" `
    -Method "GET" `
    -Url "$baseUrl/audit-logs?actionCode=LOGIN_SUCCESS&page=1&pageSize=10" `
    -Headers $adminHeaders `
    -ExpectedStatus 200 `
    -Validation { param($r) 
        $null -ne $r.items -and ($r.items.Count -eq 0 -or $r.items[0].actionCode -eq "LOGIN_SUCCESS")
    }

if ($searchByActionResult.Success) { $testsPassed++ } else { $testsFailed++ }

# Bước 17: Tìm nhật ký kiểm toán theo người dùng
Write-Host "`n--- Step 17: Search Audit by Username ---" -ForegroundColor Magenta
$searchByUserResult = Test-Endpoint `
    -Name "Search audit logs for admin user" `
    -Method "GET" `
    -Url "$baseUrl/audit-logs?userId=$adminUserId&page=1&pageSize=10" `
    -Headers $adminHeaders `
    -ExpectedStatus 200 `
    -Validation { param($r) 
        $null -ne $r.items -and ($r.items.Count -eq 0 -or $r.items[0].userId -eq $adminUserId)
    }

if ($searchByUserResult.Success) { $testsPassed++ } else { $testsFailed++ }

# Bước 18: Tìm nhật ký kiểm toán theo khoảng thời gian
Write-Host "`n--- Step 18: Search Audit with Date Range ---" -ForegroundColor Magenta
$fromDate = [DateTime]::UtcNow.Date.ToString("yyyy-MM-ddTHH:mm:ssZ")
$searchByDateResult = Test-Endpoint `
    -Name "Search audit logs for today" `
    -Method "GET" `
    -Url "$baseUrl/audit-logs?fromDate=$fromDate&page=1&pageSize=10" `
    -Headers $adminHeaders `
    -ExpectedStatus 200 `
    -Validation { param($r) $null -ne $r.items -and $r.page -eq 1 }

if ($searchByDateResult.Success) { $testsPassed++ } else { $testsFailed++ }

# Bước 19: Sắp xếp nhật ký kiểm toán theo thời gian giảm dần
Write-Host "`n--- Step 19: Search Audit with Sorting ---" -ForegroundColor Magenta
$searchSortedResult = Test-Endpoint `
    -Name "Search audit logs sorted by timestamp DESC" `
    -Method "GET" `
    -Url "$baseUrl/audit-logs?sortOrder=DESC&page=1&pageSize=5" `
    -Headers $adminHeaders `
    -ExpectedStatus 200 `
    -Validation { param($r) 
        $null -ne $r.items -and ($r.items.Count -le 5)
    }

if ($searchSortedResult.Success) { $testsPassed++ } else { $testsFailed++ }

# Bước 20: Tạo người dùng thường để kiểm tra phân quyền
Write-Host "`n--- Step 20: Create Regular User ---" -ForegroundColor Magenta
$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$createUserResult = Test-Endpoint `
    -Name "Create regular user" `
    -Method "POST" `
    -Url "$baseUrl/users" `
    -Headers $adminHeaders `
    -Body @{
        username = "testuser_$timestamp"
        email = "testuser_$timestamp@test.com"
        fullName = "Test User"
        phone = "0901234567"
        password = "Test@123"
        roleId = $newRoleId
        branchIds = @(1)
    } `
    -ExpectedStatus 201 `
    -Validation { param($r) $r.userId -and $r.username -match "testuser_" }

if ($createUserResult.Success) {
    $testsPassed++
    $testUserId = $createUserResult.Data.userId
    $testUsername = $createUserResult.Data.username
} else {
    $testsFailed++
    $testUserId = $null
    $testUsername = $null
}

# Bước 21: Người dùng thường đăng nhập
Write-Host "`n--- Step 21: Regular User Login ---" -ForegroundColor Magenta
if ($testUsername) {
    $userLoginResult = Test-Endpoint `
        -Name "Login as regular user" `
        -Method "POST" `
        -Url "$baseUrl/auth/login" `
        -Body @{
            usernameOrEmail = $testUsername
            password = "Test@123"
        } `
        -ExpectedStatus 200 `
        -Validation { param($r) $r.accessToken -and $r.user.username -eq $testUsername }
    
    if ($userLoginResult.Success) {
        $testsPassed++
        $userToken = $userLoginResult.Data.accessToken
        $userHeaders = @{ "Authorization" = "Bearer $userToken" }
    } else {
        $testsFailed++
        $userToken = $null
    }
} else {
    Write-Host "Skipping test (no test user)" -ForegroundColor Yellow
    $testsFailed++
    $userToken = $null
}

# Bước 22: Người dùng thường thử tạo vai trò (phải bị từ chối - 403)
Write-Host "`n--- Step 22: Regular User Access Control ---" -ForegroundColor Magenta
if ($userToken) {
    $userCreateRoleResult = Test-Endpoint `
        -Name "Regular user tries to create role (should fail)" `
        -Method "POST" `
        -Url "$baseUrl/roles" `
        -Headers $userHeaders `
        -Body @{
            code = "X$runId"
            name = "Test Role"
            isActive = $true
        } `
        -ExpectedStatus 403 `
        -Validation { param($r) $r.code -eq "FORBIDDEN" }
    
    if ($userCreateRoleResult.Success) { $testsPassed++ } else { $testsFailed++ }
} else {
    Write-Host "Skipping test (no user token)" -ForegroundColor Yellow
    $testsFailed++
}

# Bước 23: Người dùng thường thử xem nhật ký kiểm toán (phải bị từ chối - 403)
Write-Host "`n--- Step 23: Regular User Audit Access Control ---" -ForegroundColor Magenta
if ($userToken) {
    $userAuditResult = Test-Endpoint `
        -Name "Regular user tries to view audit logs (should fail)" `
        -Method "GET" `
        -Url "$baseUrl/audit-logs?page=1&pageSize=10" `
        -Headers $userHeaders `
        -ExpectedStatus 403 `
        -Validation { param($r) $r.code -eq "FORBIDDEN" }
    
    if ($userAuditResult.Success) { $testsPassed++ } else { $testsFailed++ }
} else {
    Write-Host "Skipping test (no user token)" -ForegroundColor Yellow
    $testsFailed++
}

# Tổng kết cuối
Write-Host "`n==================================================" -ForegroundColor Yellow
Write-Host "   Test Summary" -ForegroundColor Yellow
Write-Host "==================================================" -ForegroundColor Yellow
Write-Host "Total Passed: $testsPassed" -ForegroundColor Green
Write-Host "Total Failed: $testsFailed" -ForegroundColor Red
Write-Host "Total Tests: $($testsPassed + $testsFailed)"

if ($testsFailed -eq 0) {
    Write-Host "`nALL TESTS PASSED!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "`nSOME TESTS FAILED" -ForegroundColor Red
    exit 1
}
