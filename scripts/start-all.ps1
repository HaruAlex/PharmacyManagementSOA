<#
.SYNOPSIS
    Start all services, gateway, and frontend

.DESCRIPTION
    Khởi động tất cả services theo thứ tự:
    1. Services (auth, catalog, inventory, purchase, sales, report)
    2. Gateway
    3. Frontend (Node.js)
    
    Kiểm tra health endpoint sau mỗi khởi động
    Ghi log vào logs/ folder
    Lưu PID vào .pids/ folder

.PARAMETER Instances
    Số instances khởi động (1 hoặc 2, default: 2)

.PARAMETER Services
    Danh sách services (dùng dấu phẩy), mặc định: tất cả

.EXAMPLE
    .\start-all.ps1
    .\start-all.ps1 -Instances 1
    .\start-all.ps1 -Services "auth,inventory"

.NOTES
    Author: Nhóm 4 - Đại học Quy Nhơn
    Requirement: PowerShell 5.0+, SBT, Node.js
#>

param(
    [int]$Instances = 2,
    [string]$Services = ""
)

# ============================================================================
# Configuration
# ============================================================================

$RepoRoot = Split-Path -Parent $PSScriptRoot
$ScriptsDir = $PSScriptRoot
$ServicesConf = Join-Path $ScriptsDir "services.conf"
$LogDir = Join-Path $RepoRoot "logs"
$PidDir = Join-Path $RepoRoot ".pids"

$HealthTimeout = 60
$HealthInterval = 2

# ============================================================================
# Functions
# ============================================================================

function Write-Header {
    Write-Host "`n╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
    Write-Host "║  PHARMACY MANAGEMENT SOA - START ALL                          ║" -ForegroundColor Cyan
    Write-Host "╚════════════════════════════════════════════════════════════════╝`n" -ForegroundColor Cyan
}

function Test-Prerequisites {
    Write-Host "📋 Kiểm tra điều kiện tiên quyết..."
    
    # Check SBT
    try {
        $sbtVersion = sbt --version 2>$null
        Write-Host "  ✅ SBT: OK" -ForegroundColor Green
    }
    catch {
        Write-Host "  ❌ SBT: Không cài đặt" -ForegroundColor Red
        exit 1
    }
    
    # Check Node
    try {
        $nodeVersion = node --version 2>$null
        Write-Host "  ✅ Node.js: OK" -ForegroundColor Green
    }
    catch {
        Write-Host "  ❌ Node.js: Không cài đặt" -ForegroundColor Red
        exit 1
    }
    
    # Check Consul
    try {
        $consulResponse = Invoke-WebRequest -Uri "http://localhost:8500/ui/" -UseBasicParsing -TimeoutSec 3
        Write-Host "  ✅ Consul: Đang chạy" -ForegroundColor Green
    }
    catch {
        Write-Host "  ❌ Consul: Không chạy (http://localhost:8500)" -ForegroundColor Red
        exit 1
    }
}

function Create-Directories {
    if (-not (Test-Path $LogDir)) {
        New-Item -ItemType Directory -Path $LogDir | Out-Null
    }
    if (-not (Test-Path $PidDir)) {
        New-Item -ItemType Directory -Path $PidDir | Out-Null
    }
}

function Test-PortFree {
    param([int]$Port)
    
    try {
        $result = netstat -ano 2>$null | Select-String ":$Port "
        return $false  # Port đang dùng
    }
    catch {
        return $true   # Port libre
    }
}

function Start-Service {
    param(
        [string]$ServiceName,
        [string]$InstanceId,
        [int]$Port
    )
    
    if (-not (Test-PortFree $Port)) {
        Write-Host "⚠️  Port $Port đang sử dụng" -ForegroundColor Yellow
        return $false
    }
    
    Write-Host "🚀 Khởi động: $InstanceId (cổng $Port)"
    
    $LogFile = Join-Path $LogDir "$InstanceId.log"
    $PidFile = Join-Path $PidDir "$InstanceId.pid"
    $ServiceDir = Join-Path $RepoRoot $ServiceName
    
    if (-not (Test-Path $ServiceDir)) {
        Write-Host "  ❌ Service directory không tồn tại: $ServiceDir" -ForegroundColor Red
        return $false
    }
    
    try {
        if ($InstanceId -eq "frontend-1") {
            # Frontend (Node.js)
            Push-Location $ServiceDir
            
            if (-not (Test-Path "node_modules")) {
                Write-Host "  📦 Cài npm packages..."
                npm install 2>&1 | Out-Null
            }
            
            $env:PORT = $Port
            $process = Start-Process -FilePath "npm" -ArgumentList "start" `
                -RedirectStandardOutput $LogFile `
                -RedirectStandardError $LogFile `
                -PassThru `
                -NoNewWindow
            
            Pop-Location
        }
        else {
            # Java services
            Push-Location $ServiceDir
            
            if ($InstanceId -match "-2$") {
                # Instance 2
                $process = Start-Process -FilePath "sbt" `
                    -ArgumentList "-Dservice.instance.id=$InstanceId", "-Dservice.port=$Port", "`"run $Port`"" `
                    -RedirectStandardOutput $LogFile `
                    -RedirectStandardError $LogFile `
                    -PassThru `
                    -NoNewWindow
            }
            else {
                # Instance 1
                $process = Start-Process -FilePath "sbt" `
                    -ArgumentList "-Dservice.port=$Port", "`"run $Port`"" `
                    -RedirectStandardOutput $LogFile `
                    -RedirectStandardError $LogFile `
                    -PassThru `
                    -NoNewWindow
            }
            
            Pop-Location
        }
        
        # Lưu PID
        $process.Id | Out-File $PidFile -NoNewline
        
        # Wait for health check
        $elapsed = 0
        while ($elapsed -lt $HealthTimeout) {
            try {
                $health = Invoke-WebRequest -Uri "http://localhost:$Port/health" `
                    -UseBasicParsing -TimeoutSec 2
                
                if ($health.StatusCode -eq 200) {
                    Write-Host "  ✅ Khởi động thành công!" -ForegroundColor Green
                    return $true
                }
            }
            catch {
                # Chưa khởi động xong
            }
            
            Start-Sleep -Seconds $HealthInterval
            $elapsed += $HealthInterval
            Write-Host -NoNewline "."
        }
        
        Write-Host ""
        Write-Host "  ⚠️  Timeout chờ health check" -ForegroundColor Yellow
        return $false
    }
    catch {
        Write-Host "  ❌ Lỗi: $_" -ForegroundColor Red
        return $false
    }
}

function Start-AllServices {
    Write-Host ""
    Write-Host "📡 Khởi động Services..."
    Write-Host "==================================================" -ForegroundColor Yellow
    
    # Parse services.conf
    $content = Get-Content $ServicesConf | Where-Object { $_ -notmatch "^#" -and $_.Trim() -ne "" }
    
    foreach ($line in $content) {
        $parts = $line -split '\|'
        
        $serviceName = $parts[0].Trim()
        $consulName = $parts[1].Trim()
        $instance1Id = $parts[2].Trim()
        $instance1Port = [int]$parts[3].Trim()
        $instance2Id = if ($parts[4]) { $parts[4].Trim() } else { "" }
        $instance2Port = if ($parts[5]) { [int]$parts[5].Trim() } else { 0 }
        
        # Filter by services parameter
        if ($Services -and -not ($serviceName -match $Services)) {
            continue
        }
        
        if ($serviceName -eq "GATEWAY") {
            Start-Service "api-gateway" $instance1Id $instance1Port
        }
        elseif ($serviceName -eq "FRONTEND") {
            Start-Service "frontend" $instance1Id $instance1Port
        }
        elseif ($serviceName -ne "CONSUL") {
            # Regular services
            Start-Service "$serviceName-service" $instance1Id $instance1Port
            
            if ($Instances -eq 2 -and $instance2Id) {
                Start-Sleep -Seconds 2
                Start-Service "$serviceName-service" $instance2Id $instance2Port
            }
        }
    }
}

function Show-Status {
    Write-Host ""
    Write-Host "==================================================" -ForegroundColor Yellow
    Write-Host "📋 Bảng Server-Cổng:"
    Write-Host "==================================================" -ForegroundColor Yellow
    
    $content = Get-Content $ServicesConf | Where-Object { $_ -notmatch "^#" -and $_.Trim() -ne "" }
    
    $results = @()
    
    foreach ($line in $content) {
        $parts = $line -split '\|'
        
        $serviceName = $parts[0].Trim()
        $instance1Id = $parts[2].Trim()
        $instance1Port = $parts[3].Trim()
        $instance2Id = if ($parts[4]) { $parts[4].Trim() } else { "" }
        $instance2Port = if ($parts[5]) { $parts[5].Trim() } else { "" }
        
        if ($serviceName -eq "CONSUL") { continue }
        
        # Instance 1
        if ($instance1Id) {
            $pidFile = Join-Path $PidDir "$instance1Id.pid"
            $status = if (Test-Path $pidFile) { "✅ Running" } else { "❌ Stopped" }
            
            $results += [PSCustomObject]@{
                Service = $serviceName
                InstanceId = $instance1Id
                Port = $instance1Port
                Status = $status
            }
        }
        
        # Instance 2
        if ($instance2Id -and $instance2Id -ne "-") {
            $pidFile = Join-Path $PidDir "$instance2Id.pid"
            $status = if (Test-Path $pidFile) { "✅ Running" } else { "❌ Stopped" }
            
            $results += [PSCustomObject]@{
                Service = $serviceName
                InstanceId = $instance2Id
                Port = $instance2Port
                Status = $status
            }
        }
    }
    
    $results | Format-Table -AutoSize
}

function Show-Links {
    Write-Host ""
    Write-Host "🔗 Links Nhanh:" -ForegroundColor Green
    Write-Host "  Gateway Health:    http://localhost:9000/health"
    Write-Host "  Gateway Metrics:   http://localhost:9000/metrics"
    Write-Host "  Consul UI:         http://localhost:8500/ui/"
    Write-Host "  Frontend:          http://localhost:8080"
    Write-Host ""
}

# ============================================================================
# Main
# ============================================================================

Write-Header
Test-Prerequisites
Create-Directories
Start-AllServices
Show-Status
Show-Links

Write-Host "✅ Khởi động hoàn tất!" -ForegroundColor Green
Write-Host ""
Write-Host "Để dừng tất cả: .\stop-all.ps1" -ForegroundColor Cyan
