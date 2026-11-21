<#
.SYNOPSIS
    Show status of all services

.DESCRIPTION
    In bảng trạng thái tất cả services
    Hiển thị: Instance ID, Cổng, PID, Process Status, Health Status, Consul Status

.PARAMETER Watch
    Auto-refresh mỗi 5 giây

.EXAMPLE
    .\status.ps1
    .\status.ps1 -Watch

.NOTES
    Author: Nhóm 4 - Đại học Quy Nhơn
#>

param(
    [switch]$Watch
)

# ============================================================================
# Configuration
# ============================================================================

$RepoRoot = Split-Path -Parent $PSScriptRoot
$ScriptsDir = $PSScriptRoot
$ServicesConf = Join-Path $ScriptsDir "services.conf"
$PidDir = Join-Path $RepoRoot ".pids"

$WatchInterval = 5
$ConsulUrl = "http://localhost:8500"

# ============================================================================
# Functions
# ============================================================================

function Write-Header {
    Write-Host "`n╔════════════════════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
    Write-Host "║  PHARMACY MANAGEMENT SOA - STATUS                                         ║" -ForegroundColor Cyan
    Write-Host "║  Timestamp: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')                                       ║" -ForegroundColor Cyan
    Write-Host "╚════════════════════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
}

function Get-InstanceStatus {
    param(
        [string]$InstanceId,
        [int]$Port
    )
    
    $pidFile = Join-Path $PidDir "$InstanceId.pid"
    
    $processStatus = "❌ Stopped"
    $pid = "—"
    
    if (Test-Path $pidFile) {
        $pid = Get-Content $pidFile
        
        if (Get-Process -Id $pid -ErrorAction Ignore) {
            $processStatus = "✅ Running"
        }
    }
    
    # Health check
    $health = "—"
    if ($processStatus -eq "✅ Running") {
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:$Port/health" `
                -UseBasicParsing -TimeoutSec 2
            $health = "✅"
        }
        catch {
            $health = "❌"
        }
    }
    
    return @{
        PID = $pid
        Process = $processStatus
        Health = $health
    }
}

function Get-ConsulStatus {
    param([string]$ServiceName)
    
    try {
        $response = Invoke-WebRequest -Uri "$ConsulUrl/v1/health/service/$ServiceName" `
            -UseBasicParsing -TimeoutSec 2
        
        if ($response.Content -match '"Status":"passing"') {
            return "PASSING"
        }
        elseif ($response.Content -match '"Status":"critical"') {
            return "CRITICAL"
        }
        else {
            return "DOWN"
        }
    }
    catch {
        return "ERROR"
    }
}

function Print-StatusTable {
    Write-Host ""
    Write-Host "Instance ID            | Cổng | PID        | Process       | Health | Consul" -ForegroundColor Yellow
    Write-Host "───────────────────────┼──────┼────────────┼───────────────┼────────┼─────────" -ForegroundColor Yellow
    
    $content = Get-Content $ServicesConf | Where-Object { $_ -notmatch "^#" -and $_.Trim() -ne "" }
    
    foreach ($line in $content) {
        $parts = $line -split '\|'
        
        $serviceName = $parts[0].Trim()
        $consulName = $parts[1].Trim()
        $instance1Id = $parts[2].Trim()
        $instance1Port = [int]$parts[3].Trim()
        $instance2Id = if ($parts[4]) { $parts[4].Trim() } else { "" }
        $instance2Port = if ($parts[5]) { [int]$parts[5].Trim() } else { 0 }
        
        if ($serviceName -eq "CONSUL") { continue }
        
        # Instance 1
        if ($instance1Id -and $instance1Id -ne "-") {
            $status = Get-InstanceStatus $instance1Id $instance1Port
            
            $consul = "—"
            if ($consulName -ne "(không)") {
                $consul = Get-ConsulStatus $consulName
            }
            
            Write-Host ("{0,-23} | {1,4} | {2,10} | {3,13} | {4,6} | {5,-7}" -f `
                $instance1Id, $instance1Port, $status.PID, $status.Process, `
                $status.Health, $consul)
        }
        
        # Instance 2
        if ($instance2Id -and $instance2Id -ne "-") {
            $status = Get-InstanceStatus $instance2Id $instance2Port
            
            $consul = "—"
            if ($consulName -ne "(không)") {
                $consul = Get-ConsulStatus $consulName
            }
            
            Write-Host ("{0,-23} | {1,4} | {2,10} | {3,13} | {4,6} | {5,-7}" -f `
                $instance2Id, $instance2Port, $status.PID, $status.Process, `
                $status.Health, $consul)
        }
    }
}

function Print-Summary {
    Write-Host ""
    Write-Host "───────────────────────────────────────────────────────────────────────────" -ForegroundColor Yellow
    
    $running = 0
    $total = 0
    
    Get-ChildItem $PidDir -Filter "*.pid" -ErrorAction Ignore | ForEach-Object {
        $total++
        $pid = Get-Content $_.FullName
        
        if (Get-Process -Id $pid -ErrorAction Ignore) {
            $running++
        }
    }
    
    if ($total -eq 0) {
        Write-Host "📊 Tóm tắt: Không có service nào chạy" -ForegroundColor Yellow
    }
    else {
        Write-Host "📊 Tóm tắt: $running / $total services đang chạy" -ForegroundColor Green
    }
    
    # Check Consul
    try {
        $consulCheck = Invoke-WebRequest -Uri "$ConsulUrl/ui/" -UseBasicParsing -TimeoutSec 2
        Write-Host "✅ Consul: Đang chạy ($ConsulUrl)" -ForegroundColor Green
    }
    catch {
        Write-Host "❌ Consul: Không chạy" -ForegroundColor Red
    }
}

function Print-Links {
    Write-Host ""
    Write-Host "🔗 Links Nhanh:" -ForegroundColor Green
    Write-Host "  - Consul UI:       $ConsulUrl/ui/"
    Write-Host "  - Gateway Health:  http://localhost:9000/health"
    Write-Host "  - Frontend:        http://localhost:8080"
    Write-Host ""
}

function Watch-Mode {
    while ($true) {
        Clear-Host
        Write-Header
        Print-StatusTable
        Print-Summary
        Print-Links
        
        Write-Host "⏱️  Auto-refresh trong $WatchInterval giây (Ctrl+C để thoát)..." -ForegroundColor Cyan
        Start-Sleep -Seconds $WatchInterval
    }
}

# ============================================================================
# Main
# ============================================================================

Write-Header

if ($Watch) {
    Watch-Mode
}
else {
    Print-StatusTable
    Print-Summary
    Print-Links
}
