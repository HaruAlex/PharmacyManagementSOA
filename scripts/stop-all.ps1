<#
.SYNOPSIS
    Stop all services

.DESCRIPTION
    Dừng tất cả services một cách graceful:
    1. Dừng Frontend
    2. Dừng Gateway
    3. Dừng Services (ngược thứ tự)
    
    Đọc PID từ .pids/ folder

.PARAMETER Force
    Dừng ngay (kill -9) thay vì graceful shutdown

.PARAMETER Instance
    Dừng riêng một instance cụ thể

.EXAMPLE
    .\stop-all.ps1
    .\stop-all.ps1 -Force
    .\stop-all.ps1 -Instance "inventory-1"

.NOTES
    Author: Nhóm 4 - Đại học Quy Nhơn
#>

param(
    [switch]$Force,
    [string]$Instance = ""
)

# ============================================================================
# Configuration
# ============================================================================

$RepoRoot = Split-Path -Parent $PSScriptRoot
$ScriptsDir = $PSScriptRoot
$ServicesConf = Join-Path $ScriptsDir "services.conf"
$PidDir = Join-Path $RepoRoot ".pids"

$GracefulTimeout = 10

# ============================================================================
# Functions
# ============================================================================

function Write-Header {
    Write-Host "`n╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
    Write-Host "║  PHARMACY MANAGEMENT SOA - STOP ALL                           ║" -ForegroundColor Cyan
    Write-Host "╚════════════════════════════════════════════════════════════════╝`n" -ForegroundColor Cyan
}

function Stop-ServiceInstance {
    param(
        [string]$InstanceId
    )
    
    $pidFile = Join-Path $PidDir "$InstanceId.pid"
    
    if (-not (Test-Path $pidFile)) {
        Write-Host "⚠️  $InstanceId: PID file không tồn tại" -ForegroundColor Yellow
        return
    }
    
    $pid = Get-Content $pidFile
    
    # Check if process exists
    try {
        $process = Get-Process -Id $pid -ErrorAction Stop
    }
    catch {
        Write-Host "⚠️  $InstanceId (PID $pid): Process không tồn tại" -ForegroundColor Yellow
        Remove-Item $pidFile -Force -ErrorAction Ignore
        return
    }
    
    Write-Host "🛑 Dừng: $InstanceId (PID $pid)"
    
    if ($Force) {
        # Kill ngay
        Stop-Process -Id $pid -Force -ErrorAction Ignore
        Write-Host "  ⚡ Killed (force)" -ForegroundColor Yellow
    }
    else {
        # Graceful shutdown (SIGTERM)
        try {
            $process.Kill($false)  # Soft kill
        }
        catch {
            # Fallback
            Stop-Process -Id $pid -ErrorAction Ignore
        }
        
        # Wait for process to exit
        $elapsed = 0
        while ((Get-Process -Id $pid -ErrorAction Ignore) -and $elapsed -lt $GracefulTimeout) {
            Start-Sleep -Seconds 1
            $elapsed += 1
            Write-Host -NoNewline "."
        }
        
        # If still running, force kill
        if (Get-Process -Id $pid -ErrorAction Ignore) {
            Write-Host ""
            Write-Host "  ⚠️  Process vẫn chạy, force kill..." -ForegroundColor Yellow
            Stop-Process -Id $pid -Force -ErrorAction Ignore
        }
        else {
            Write-Host ""
            Write-Host "  ✅ Đã tắt gracefully" -ForegroundColor Green
        }
    }
    
    Remove-Item $pidFile -Force -ErrorAction Ignore
}

function Stop-AllServices {
    Write-Host ""
    Write-Host "Dừng Services..."
    Write-Host "==================================================" -ForegroundColor Yellow
    
    # Nếu chỉ định instance
    if ($Instance) {
        Stop-ServiceInstance $Instance
        return
    }
    
    # Thứ tự dừng ngược: Frontend → Gateway → Services
    
    # Stop Frontend
    $frontendPid = Join-Path $PidDir "frontend-1.pid"
    if (Test-Path $frontendPid) {
        Stop-ServiceInstance "frontend-1"
    }
    
    Start-Sleep -Seconds 1
    
    # Stop Gateway
    $gatewayPid = Join-Path $PidDir "gateway-1.pid"
    if (Test-Path $gatewayPid) {
        Stop-ServiceInstance "gateway-1"
    }
    
    Start-Sleep -Seconds 1
    
    # Stop Services
    $content = Get-Content $ServicesConf | Where-Object { $_ -notmatch "^#" -and $_.Trim() -ne "" }
    
    $instances = @()
    
    foreach ($line in $content) {
        $parts = $line -split '\|'
        
        $serviceName = $parts[0].Trim()
        
        if ($serviceName -ne "GATEWAY" -and $serviceName -ne "FRONTEND" -and $serviceName -ne "CONSUL") {
            $instance1Id = $parts[2].Trim()
            $instance2Id = if ($parts[4]) { $parts[4].Trim() } else { "" }
            
            if ($instance1Id) { $instances += $instance1Id }
            if ($instance2Id -and $instance2Id -ne "-") { $instances += $instance2Id }
        }
    }
    
    # Dừng theo thứ tự ngược
    for ($i = $instances.Count - 1; $i -ge 0; $i--) {
        Stop-ServiceInstance $instances[$i]
    }
}

function Verify-Stopped {
    Write-Host ""
    Write-Host "📋 Kiểm tra các process đã tắt..."
    Write-Host "==================================================" -ForegroundColor Yellow
    
    $running = 0
    
    Get-ChildItem $PidDir -Filter "*.pid" -ErrorAction Ignore | ForEach-Object {
        $pid = Get-Content $_.FullName
        
        if (Get-Process -Id $pid -ErrorAction Ignore) {
            $instanceId = $_.BaseName
            Write-Host "⚠️  $instanceId (PID $pid) vẫn còn chạy!" -ForegroundColor Yellow
            $running++
        }
    }
    
    if ($running -eq 0) {
        Write-Host "✅ Tất cả process đã tắt" -ForegroundColor Green
        
        # Clean up PID files
        Remove-Item (Join-Path $PidDir "*.pid") -Force -ErrorAction Ignore
        
        return $true
    }
    else {
        Write-Host "❌ Còn $running process(s) chạy" -ForegroundColor Red
        return $false
    }
}

# ============================================================================
# Main
# ============================================================================

Write-Header

if ($Instance) {
    Write-Host "Dừng instance: $Instance" -ForegroundColor Yellow
}
else {
    Write-Host "Dừng tất cả services" -ForegroundColor Yellow
}

Write-Host ""

Stop-AllServices
Verify-Stopped

Write-Host ""
Write-Host "✅ Dừng hoàn tất!" -ForegroundColor Green
Write-Host ""
Write-Host "Để khởi động lại: .\start-all.ps1" -ForegroundColor Cyan
