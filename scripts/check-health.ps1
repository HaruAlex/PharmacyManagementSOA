<#
.SYNOPSIS
    Check health của tất cả services qua Gateway

.DESCRIPTION
    Script kiểm tra /health endpoint của Gateway và các services
    In kết quả bảng: Service | Port | Status | Response Time

.PARAMETER Endpoint
    Endpoint để check (default: /health)

.EXAMPLE
    .\check-health.ps1
    .\check-health.ps1 -Endpoint "/healthz"
    .\check-health.ps1 -Endpoint "/metrics"

.NOTES
    Author: Nhóm 4 - Đại học Quy Nhơn
    Requirement: PowerShell 3.0+
#>

param(
    [string]$Endpoint = "/health"
)

# ============================================================================
# Configuration
# ============================================================================

$GatewayUrl = "http://localhost:9000"
$TimeoutSeconds = 5

$Services = @(
    @{ Name = "Gateway"; Port = 9000; Url = "http://localhost:9000" },
    @{ Name = "Auth (Instance 1)"; Port = 9001; Url = "http://localhost:9001" },
    @{ Name = "Auth (Instance 2)"; Port = 9011; Url = "http://localhost:9011" },
    @{ Name = "Catalog (Instance 1)"; Port = 9002; Url = "http://localhost:9002" },
    @{ Name = "Catalog (Instance 2)"; Port = 9012; Url = "http://localhost:9012" },
    @{ Name = "Inventory (Instance 1)"; Port = 9003; Url = "http://localhost:9003" },
    @{ Name = "Inventory (Instance 2)"; Port = 9013; Url = "http://localhost:9013" },
    @{ Name = "Purchase (Instance 1)"; Port = 9004; Url = "http://localhost:9004" },
    @{ Name = "Purchase (Instance 2)"; Port = 9014; Url = "http://localhost:9014" },
    @{ Name = "Sales (Instance 1)"; Port = 9005; Url = "http://localhost:9005" },
    @{ Name = "Sales (Instance 2)"; Port = 9015; Url = "http://localhost:9015" },
    @{ Name = "Report (Instance 1)"; Port = 9006; Url = "http://localhost:9006" },
    @{ Name = "Report (Instance 2)"; Port = 9016; Url = "http://localhost:9016" }
)

# ============================================================================
# Main Logic
# ============================================================================

Write-Host "`n╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║  HEALTH CHECK - PHARMACY MANAGEMENT SOA                        ║" -ForegroundColor Cyan
Write-Host "║  Endpoint: $Endpoint                                              ║" -ForegroundColor Cyan
Write-Host "║  Timestamp: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')                             ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════════════╝`n" -ForegroundColor Cyan

$results = @()
$upCount = 0
$downCount = 0

foreach ($service in $Services) {
    $url = "$($service.Url)$Endpoint"

    try {
        $startTime = Get-Date
        $response = Invoke-WebRequest -Uri $url -TimeoutSec $TimeoutSeconds -UseBasicParsing -ErrorAction Stop
        $endTime = Get-Date
        $responseTime = ($endTime - $startTime).TotalMilliseconds

        $status = "✅ UP"
        $upCount++

        $results += [PSCustomObject]@{
            Service = $service.Name
            Port = $service.Port
            Status = $status
            "Response Time (ms)" = [math]::Round($responseTime, 2)
            "HTTP Code" = $response.StatusCode
        }
    }
    catch {
        $status = "❌ DOWN"
        $downCount++

        $results += [PSCustomObject]@{
            Service = $service.Name
            Port = $service.Port
            Status = $status
            "Response Time (ms)" = "-"
            "HTTP Code" = "N/A"
        }
    }
}

# Print table
$results | Format-Table -AutoSize -Property Service, Port, Status, "Response Time (ms)", "HTTP Code"

# Summary
Write-Host "`n╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║  SUMMARY                                                       ║" -ForegroundColor Cyan
Write-Host "║  UP: $upCount | DOWN: $downCount                                             ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════════════╝`n" -ForegroundColor Cyan

if ($downCount -eq 0) {
    Write-Host "✅ Tất cả services healthy!" -ForegroundColor Green
    exit 0
}
else {
    Write-Host "⚠️ Có $downCount service(s) không phản hồi" -ForegroundColor Yellow
    exit 1
}