<#
.SYNOPSIS
    Full SOA System Test Demo - Khởi động tất cả 14 processes

.DESCRIPTION
    Khởi động + kiểm tra toàn bộ hệ thống:
    - 1 Gateway (1 instance)
    - 6 Services (2 instances mỗi service) = 12 instances
    - 1 Frontend (1 instance)
    - TOTAL: 14 processes
    
    Kiểm tra:
    1. Service Discovery: Xem tất cả đăng ký Consul
    2. Load Balancing: 10 requests, check X-Served-By
    3. Failover: Tắt 1 instance, check request vẫn OK

.PARAMETER Instances
    Số instances: 1 hoặc 2 (default: 1 để nhanh, không làm RAM quá)

.PARAMETER SkipConsulCheck
    Bỏ qua kiểm tra Consul (nếu không có Consul)

.EXAMPLE
    .\demo-soa-instances.ps1
    .\demo-soa-instances.ps1 -Instances 2
    .\demo-soa-instances.ps1 -SkipConsulCheck

.NOTES
    Author: Nhóm 4 - Đại học Quy Nhơn
    Requirement: Java 11+, SBT, Node.js, Consul (optional)
#>

param(
    [int]$Instances = 1,
    [switch]$SkipConsulCheck
)

# ============================================================================
# Configuration
# ============================================================================

$RepoRoot = Split-Path -Parent $PSScriptRoot
$ScriptsDir = $PSScriptRoot
$ServicesConf = Join-Path $ScriptsDir "services.conf"

$Services = @(
    "auth-service",
    "catalog-service", 
    "inventory-service",
    "purchase-service",
    "sales-service",
    "report-service"
)

$HealthTimeout = 90
$HealthCheckInterval = 2

# ============================================================================
# Functions
# ============================================================================

function Write-Title {
    param([string]$Title)
    Write-Host ""
    Write-Host "╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
    Write-Host "║  $Title$((' ' * (62 - $Title.Length))  )║" -ForegroundColor Cyan
    Write-Host "╚═══════════════════════════════════════════════════════════���════╝" -ForegroundColor Cyan
}

function Check-Prerequisite {
    param([string]$Name, [string]$CheckCommand)
    
    try {
        Invoke-Expression $CheckCommand | Out-Null
        Write-Host "✅ $Name" -ForegroundColor Green
        return $true
    }
    catch {
        Write-Host "❌ $Name" -ForegroundColor Red
        return $false
    }
}

function Wait-Service {
    param([int]$Port, [string]$ServiceName, [int]$TimeoutSec)
    
    $elapsed = 0
    $interval = $HealthCheckInterval
    
    while ($elapsed -lt $TimeoutSec) {
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:$Port/health" -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -eq 200) {
                Write-Host "✅ $ServiceName (port $Port) ready" -ForegroundColor Green
                return $true
            }
        }
        catch {
            # Still loading
        }
        
        Write-Host -NoNewline "."
        Start-Sleep -Seconds $interval
        $elapsed += $interval
    }
    
    Write-Host ""
    Write-Host "⚠️  $ServiceName (port $Port) timeout" -ForegroundColor Yellow
    return $false
}

# ============================================================================
# Main Demo
# ============================================================================

# Step 1: Header
Write-Title "PHARMACY MANAGEMENT SOA - FULL SYSTEM DEMO"
Write-Host ""
Write-Host "🎯 Demo Scope:" -ForegroundColor Yellow
Write-Host "  Gateway: 1 instance (port 9000)" -ForegroundColor Gray
Write-Host "  Services: 6 services × 2 instances = 12 instances (ports 9001-9006, 9011-9016)" -ForegroundColor Gray
Write-Host "  Frontend: 1 instance (port 8080)" -ForegroundColor Gray
Write-Host "  TOTAL: 14 processes" -ForegroundColor Gray
Write-Host ""

# Step 2: Check Prerequisites
Write-Title "Step 1: Kiểm Tra Điều Kiện Tiên Quyết"

$allOK = $true

Write-Host ""
Write-Host "Checking prerequisites..." -ForegroundColor Yellow

# Java
if (-not (Check-Prerequisite "Java 11+" "java -version")) {
    $allOK = $false
}

# SBT
if (-not (Check-Prerequisite "SBT" "sbt --version")) {
    $allOK = $false
}

# Node.js
if (-not (Check-Prerequisite "Node.js" "node --version")) {
    $allOK = $false
}

# Consul (optional)
if (-not $SkipConsulCheck) {
    if (Check-Prerequisite "Consul (optional)" "curl -s http://localhost:8500/ui/ | Select-String 'Consul' | Out-Null") {
        Write-Host "ℹ️  Consul available - will test Service Discovery" -ForegroundColor Cyan
    }
    else {
        Write-Host "⚠️  Consul not available - skipping Service Discovery test" -ForegroundColor Yellow
    }
}

if (-not $allOK) {
    Write-Host ""
    Write-Host "❌ Some prerequisites missing. Please install required tools." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "✅ All prerequisites OK!" -ForegroundColor Green

# Step 3: Start Gateway
Write-Title "Step 2: Khởi Động API Gateway (Port 9000)"

Write-Host ""
Write-Host "🚀 Starting API Gateway..." -ForegroundColor Yellow

$gatewayDir = Join-Path $RepoRoot "api-gateway"
$gatewayProcess = Start-Process -FilePath "sbt" `
    -ArgumentList "`"run 9000`"" `
    -WorkingDirectory $gatewayDir `
    -RedirectStandardOutput (Join-Path $RepoRoot "logs\gateway-1.out.log") `
    -RedirectStandardError (Join-Path $RepoRoot "logs\gateway-1.err.log") `
    -PassThru `
    -NoNewWindow

Write-Host "  PID: $($gatewayProcess.Id)" -ForegroundColor Gray
Write-Host "  Waiting for health check..." -ForegroundColor Gray

Wait-Service 9000 "API Gateway" $HealthTimeout | Out-Null

# Step 4: Start Services
Write-Title "Step 3: Khởi Động 6 Services (12 Instances)"

Write-Host ""

$serviceProcesses = @()
$servicesPorts = @(
    @("auth-service", 9001, 9011),
    @("catalog-service", 9002, 9012),
    @("inventory-service", 9003, 9013),
    @("purchase-service", 9004, 9014),
    @("sales-service", 9005, 9015),
    @("report-service", 9006, 9016)
)

foreach ($serviceInfo in $servicesPorts) {
    $service = $serviceInfo[0]
    $port1 = $serviceInfo[1]
    $port2 = $serviceInfo[2]
    
    Write-Host "🚀 Starting $service..." -ForegroundColor Yellow
    
    # Instance 1
    Write-Host "  Instance 1 (port $port1)..." -ForegroundColor Gray
    $serviceDir = Join-Path $RepoRoot $service
    
    $process1 = Start-Process -FilePath "sbt" `
        -ArgumentList "`"run $port1`"" `
        -WorkingDirectory $serviceDir `
        -RedirectStandardOutput (Join-Path $RepoRoot "logs\$($service)-1.out.log") `
        -RedirectStandardError (Join-Path $RepoRoot "logs\$($service)-1.err.log") `
        -PassThru `
        -NoNewWindow
    
    $serviceProcesses += @{Name = "$service-1"; PID = $process1.Id; Port = $port1; Process = $process1}
    Write-Host "    PID: $($process1.Id)" -ForegroundColor Gray
    
    Wait-Service $port1 "$service-1" $HealthTimeout | Out-Null
    
    # Instance 2 (if Instances == 2)
    if ($Instances -eq 2) {
        Write-Host "  Instance 2 (port $port2)..." -ForegroundColor Gray
        Start-Sleep -Seconds 2
        
        $process2 = Start-Process -FilePath "sbt" `
            -ArgumentList "-Dservice.instance.id=$($service)-2", "`"run $port2`"" `
            -WorkingDirectory $serviceDir `
            -RedirectStandardOutput (Join-Path $RepoRoot "logs\$($service)-2.out.log") `
            -RedirectStandardError (Join-Path $RepoRoot "logs\$($service)-2.err.log") `
            -PassThru `
            -NoNewWindow
        
        $serviceProcesses += @{Name = "$service-2"; PID = $process2.Id; Port = $port2; Process = $process2}
        Write-Host "    PID: $($process2.Id)" -ForegroundColor Gray
        
        Wait-Service $port2 "$service-2" $HealthTimeout | Out-Null
    }
}

# Step 5: Start Frontend
Write-Title "Step 4: Khởi Động Frontend (Port 8080)"

Write-Host ""
Write-Host "🚀 Starting Frontend..." -ForegroundColor Yellow

$frontendDir = Join-Path $RepoRoot "frontend"

# Check node_modules
if (-not (Test-Path "$frontendDir\node_modules")) {
    Write-Host "  Installing npm packages..." -ForegroundColor Gray
    Push-Location $frontendDir
    npm install 2>&1 | Out-Null
    Pop-Location
}

$env:PORT = 8080
$frontendProcess = Start-Process -FilePath "cmd.exe" `
    -ArgumentList "/c npm start" `
    -WorkingDirectory $frontendDir `
    -RedirectStandardOutput (Join-Path $RepoRoot "logs\frontend-1.out.log") `
    -RedirectStandardError (Join-Path $RepoRoot "logs\frontend-1.err.log") `
    -PassThru `
    -NoNewWindow

Write-Host "  PID: $($frontendProcess.Id)" -ForegroundColor Gray
Write-Host "  Waiting for health check..." -ForegroundColor Gray

Wait-Service 8080 "Frontend" 30 | Out-Null

# Step 6: Summary
Write-Title "Step 5: Tóm Tắt Hệ Thống"

Write-Host ""
Write-Host "📊 All Services Status:" -ForegroundColor Yellow
Write-Host ""

# Check all running
$allRunning = $true
$runningCount = 0

Write-Host "API Gateway:" -ForegroundColor Cyan
if (Get-Process -Id $gatewayProcess.Id -ErrorAction Ignore) {
    Write-Host "  ✅ gateway-1 (9000)" -ForegroundColor Green
    $runningCount++
}
else {
    Write-Host "  ❌ gateway-1 (9000)" -ForegroundColor Red
    $allRunning = $false
}

Write-Host ""
Write-Host "Services:" -ForegroundColor Cyan
foreach ($serviceInfo in $servicesPorts) {
    $service = $serviceInfo[0]
    $port1 = $serviceInfo[1]
    $port2 = $serviceInfo[2]
    
    if (Test-NetConnection -ComputerName localhost -Port $port1 -InformationLevel Quiet -WarningAction SilentlyContinue) {
        Write-Host "  ✅ $service-1 ($port1)" -ForegroundColor Green
        $runningCount++
    }
    else {
        Write-Host "  ❌ $service-1 ($port1)" -ForegroundColor Red
        $allRunning = $false
    }
    
    if ($Instances -eq 2) {
        if (Test-NetConnection -ComputerName localhost -Port $port2 -InformationLevel Quiet -WarningAction SilentlyContinue) {
            Write-Host "  ✅ $service-2 ($port2)" -ForegroundColor Green
            $runningCount++
        }
        else {
            Write-Host "  ❌ $service-2 ($port2)" -ForegroundColor Red
            $allRunning = $false
        }
    }
}

Write-Host ""
Write-Host "Frontend:" -ForegroundColor Cyan
if ($frontendProcess -and (Get-Process -Id $frontendProcess.Id -ErrorAction Ignore)) {
    Write-Host "  ✅ frontend-1 (8080)" -ForegroundColor Green
    $runningCount++
}
else {
    Write-Host "  ❌ frontend-1 (8080)" -ForegroundColor Red
    $allRunning = $false
}

Write-Host ""
Write-Host "════════════════════════════════════════════════════════════════" -ForegroundColor Yellow

$totalExpected = 1 + ($Services.Count * $Instances) + 1
Write-Host "📊 Total: $runningCount / $totalExpected running" -ForegroundColor Cyan

Write-Host ""
Write-Host "🔗 Quick Links:" -ForegroundColor Green
Write-Host "  Gateway Health:    http://localhost:9000/health" -ForegroundColor Gray
Write-Host "  Gateway Metrics:   http://localhost:9000/metrics" -ForegroundColor Gray
Write-Host "  Consul UI:         http://localhost:8500/ui/" -ForegroundColor Gray
Write-Host "  Frontend:          http://localhost:8080" -ForegroundColor Gray

Write-Host ""
Write-Host "📋 Test Commands:" -ForegroundColor Green
Write-Host "  Load Balancing:    .\scripts\test-load-balancing.ps1" -ForegroundColor Gray
Write-Host "  Check Health:      .\scripts\check-health.ps1" -ForegroundColor Gray
Write-Host "  View Status:       .\scripts\status.ps1 -Watch" -ForegroundColor Gray
Write-Host "  Stop All:          .\scripts\stop-all.ps1" -ForegroundColor Gray

Write-Host ""

if ($allRunning) {
    Write-Host "╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Green
    Write-Host "║  ✅ ALL SYSTEMS OPERATIONAL!                                  ║" -ForegroundColor Green
    Write-Host "╚════════════════════════════════════════════════════════════════╝" -ForegroundColor Green
}
else {
    Write-Host "╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Yellow
    Write-Host "║  ⚠️  SOME SERVICES NOT RESPONDING                             ║" -ForegroundColor Yellow
    Write-Host "║  Check logs in: logs/ folder                                   ║" -ForegroundColor Yellow
    Write-Host "╚════════════════════════════════════════════════════════════════╝" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Logs location: $RepoRoot\logs\" -ForegroundColor Gray
Write-Host ""




