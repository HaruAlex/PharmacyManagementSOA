<#
.SYNOPSIS
    Test Load Balancing - Gửi N request và kiểm tra X-Served-By header

.DESCRIPTION
    Script gửi nhiều request qua Gateway và thống kê:
    - Mỗi instance nhận bao nhiêu request
    - Round-robin có hoạt động không

.PARAMETER RequestCount
    Số request gửi (default: 10)

.PARAMETER Endpoint
    Endpoint để test (default: /health)

.EXAMPLE
    .\test-load-balancing.ps1                    # 10 requests
    .\test-load-balancing.ps1 -RequestCount 20   # 20 requests
    .\test-load-balancing.ps1 -Endpoint "/api/v1/inventory/stock"

.NOTES
    Author: Nhóm 4 - Đại học Quy Nhơn
    Kỳ vọng: X-Served-By sẽ xen kẽ giữa các instance (Round-robin)
#>

param(
    [int]$RequestCount = 10,
    [string]$Endpoint = "/health"
)

# ============================================================================
# Configuration
# ============================================================================

$GatewayUrl = "http://localhost:9000"
$TimeoutSeconds = 5

# ============================================================================
# Main Logic
# ============================================================================

Write-Host "`n╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║  LOAD BALANCING TEST - ROUND ROBIN                            ║" -ForegroundColor Cyan
Write-Host "║  Requests: $RequestCount                                               ║" -ForegroundColor Cyan
Write-Host "║  Endpoint: $Endpoint                                               ║" -ForegroundColor Cyan
Write-Host "║  Gateway: $GatewayUrl                                    ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════════════╝`n" -ForegroundColor Cyan

$results = @()
$stats = @{}

Write-Host "Gửi $RequestCount requests..."

for ($i = 1; $i -le $RequestCount; $i++) {
    try {
        $url = "$GatewayUrl$Endpoint"
        $response = Invoke-WebRequest -Uri $url -TimeoutSec $TimeoutSeconds -UseBasicParsing -ErrorAction Stop
        
        $servedBy = $response.Headers["X-Served-By"]
        if (-not $servedBy) {
            $servedBy = "unknown"
        }
        
        $results += [PSCustomObject]@{
            Request = $i
            Instance = $servedBy
            Status = "✅"
            "HTTP Code" = $response.StatusCode
        }
        
        # Thống kê
        if ($stats[$servedBy]) {
            $stats[$servedBy]++
        }
        else {
            $stats[$servedBy] = 1
        }
        
        Write-Host -NoNewline "."
    }
    catch {
        $results += [PSCustomObject]@{
            Request = $i
            Instance = "ERROR"
            Status = "❌"
            "HTTP Code" = "N/A"
        }
        
        Write-Host -NoNewline "✗"
    }
}

Write-Host "`n"

# Print request log
Write-Host "`n📋 REQUEST LOG:" -ForegroundColor Yellow
$results | Format-Table -AutoSize -Property Request, Instance, Status, "HTTP Code"

# Print statistics
Write-Host "`n📊 STATISTICS:" -ForegroundColor Yellow

$totalRequests = $results.Count
$successCount = ($results | Where-Object { $_.Status -eq "✅" }).Count

$statTable = @()
foreach ($instance in ($stats.Keys | Sort-Object)) {
    $count = $stats[$instance]
    $percentage = [math]::Round(($count / $totalRequests * 100), 2)
    
    $statTable += [PSCustomObject]@{
        Instance = $instance
        Requests = $count
        Percentage = "$percentage%"
    }
}

$statTable | Format-Table -AutoSize

Write-Host "`n╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║  SUMMARY                                                       ║" -ForegroundColor Cyan
Write-Host "║  Total Requests: $RequestCount                                        ║" -ForegroundColor Cyan
Write-Host "║  Success: $successCount                                                 ║" -ForegroundColor Cyan
Write-Host "║  Failed: $($RequestCount - $successCount)                                                   ║" -ForegroundColor Cyan
Write-Host "║  Instances Served: $($stats.Count)                                                ║" -ForegroundColor Cyan

# Check round-robin
$instances = @($stats.Keys | Sort-Object)
$isRoundRobin = $instances.Count -gt 1
$isBalanced = $true

if ($isRoundRobin) {
    $avgRequests = $successCount / $instances.Count
    $variance = 0
    
    foreach ($count in $stats.Values) {
        $variance += [math]::Abs($count - $avgRequests)
    }
    
    $variance = $variance / $instances.Count
    $isBalanced = $variance -lt 2  # Allow 1-2 request difference
    
    if ($isBalanced) {
        Write-Host "║  Load Distribution: ✅ BALANCED (Round-robin working)        ║" -ForegroundColor Green
    }
    else {
        Write-Host "║  Load Distribution: ⚠️  IMBALANCED (Check round-robin)      ║" -ForegroundColor Yellow
    }
}
else {
    Write-Host "║  Load Distribution: ⚠️  Only 1 instance responded           ║" -ForegroundColor Yellow
}

Write-Host "╚════════════════════════════════════════════════════════════════╝`n" -ForegroundColor Cyan

if ($successCount -eq $RequestCount -and ($isBalanced -or -not $isRoundRobin)) {
    Write-Host "✅ Load Balancing Test PASSED" -ForegroundColor Green
    exit 0
}
else {
    Write-Host "❌ Load Balancing Test FAILED" -ForegroundColor Red
    exit 1
}
