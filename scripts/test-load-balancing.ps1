param(
    [int]$RequestCount = 10,
    [string]$Endpoint = "/health",
    [string]$GatewayUrl = "http://localhost:9000"
)

Write-Host "`n╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║  LOAD BALANCING TEST - ROUND ROBIN                            ║" -ForegroundColor Cyan
Write-Host "║  Requests: $RequestCount                                               ║" -ForegroundColor Cyan
Write-Host "║  Endpoint: $Endpoint                                               ║" -ForegroundColor Cyan
Write-Host "║  Gateway: $GatewayUrl                                    ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════════════╝`n" -ForegroundColor Cyan

$results = @()
$stats = @{}

Write-Host "Gửi $RequestCount requests..." -ForegroundColor Gray

for ($i = 1; $i -le $RequestCount; $i++) {
    try {
        $uri = "$GatewayUrl$Endpoint"
        $resp = Invoke-WebRequest -Uri $uri -TimeoutSec 5 -UseBasicParsing -ErrorAction Stop

        $instance = $resp.Headers["X-Served-By"]
        if (-not $instance) {
            $instance = "unknown"
        }

        $results += [PSCustomObject]@{
            Request = $i
            Instance = $instance
            Status = "✅"
            "HTTP Code" = $resp.StatusCode
        }

        if ($stats.ContainsKey($instance)) {
            $stats[$instance]++
        }
        else {
            $stats[$instance] = 1
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

Write-Host "📋 REQUEST LOG:" -ForegroundColor Yellow
$results | Format-Table -AutoSize

Write-Host "📊 STATISTICS:" -ForegroundColor Yellow

$total = $results.Count
$success = ($results | Where-Object { $_.Status -eq "✅" }).Count
$failed = $total - $success

$statRows = @()
foreach ($key in ($stats.Keys | Sort-Object)) {
    $count = $stats[$key]
    $pct = if ($total -gt 0) { [math]::Round(($count / $total * 100), 2) } else { 0 }

    $statRows += [PSCustomObject]@{
        Instance = $key
        Requests = $count
        Percentage = "$pct%"
    }
}

if ($statRows.Count -gt 0) {
    $statRows | Format-Table -AutoSize
}

$distinctInstances = ($stats.Keys | Where-Object { $_ -ne "ERROR" }).Count
$distNote = if ($distinctInstances -gt 1) {
    "✅ Round-robin across $distinctInstances instances"
}
elseif ($distinctInstances -eq 1) {
    "⚠️  Only 1 instance responded"
}
else {
    "❌ No instance responded"
}

Write-Host "╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║  SUMMARY                                                       ║" -ForegroundColor Cyan
Write-Host "║  Total Requests: $total                                        ║" -ForegroundColor Cyan
Write-Host "║  Success: $success                                             ║" -ForegroundColor Cyan
Write-Host "║  Failed: $failed                                               ║" -ForegroundColor Cyan
Write-Host "║  Instances Served: $distinctInstances                          ║" -ForegroundColor Cyan
Write-Host "║  Load Distribution: $distNote                                  ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════════════╝`n" -ForegroundColor Cyan

if ($success -gt 0 -and $failed -eq 0) {
    Write-Host "✅ Load Balancing Test PASSED" -ForegroundColor Green
    exit 0
}
else {
    Write-Host "❌ Load Balancing Test FAILED" -ForegroundColor Red
    exit 1
}