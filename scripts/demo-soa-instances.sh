#!/bin/bash
# ============================================================================
# DEMO-SOA-INSTANCES.SH - Full System Test Demo
# ============================================================================
#
# Mục đích: Khởi động toàn bộ hệ thống (14 processes) và check
# - 1 Gateway
# - 6 Services × 2 instances = 12 instances
# - 1 Frontend
# - TOTAL: 14 processes
#
# Cách chạy:
#   bash scripts/demo-soa-instances.sh              # 1 instance/service
#   bash scripts/demo-soa-instances.sh --instances 2   # 2 instances/service
#   bash scripts/demo-soa-instances.sh --skip-consul   # Skip Consul check
#
# @author Nhóm 4 - Đại học Quy Nhơn
# ============================================================================

set -e

# ============================================================================
# Configuration
# ============================================================================

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPTS_DIR="$REPO_ROOT/scripts"
LOG_DIR="$REPO_ROOT/logs"

INSTANCES=1
SKIP_CONSUL=0
HEALTH_TIMEOUT=90

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --instances)
            INSTANCES="$2"
            shift 2
            ;;
        --skip-consul)
            SKIP_CONSUL=1
            shift
            ;;
        *)
            shift
            ;;
    esac
done

# ============================================================================
# Functions
# ============================================================================

print_header() {
    local title=$1
    echo ""
    echo "╔════════════════════════════════════════════════════════════════╗"
    echo "║  $title"
    echo "╚════════════════════════════════════════════════════════════════╝"
}

check_prerequisite() {
    local name=$1
    local command=$2
    
    if eval "$command" > /dev/null 2>&1; then
        echo "✅ $name"
        return 0
    else
        echo "❌ $name"
        return 1
    fi
}

wait_service() {
    local port=$1
    local name=$2
    local timeout=$3
    
    local elapsed=0
    
    while [ $elapsed -lt $timeout ]; do
        if curl -s "http://localhost:$port/health" > /dev/null 2>&1; then
            echo "✅ $name (port $port) ready"
            return 0
        fi
        
        echo -n "."
        sleep 2
        elapsed=$((elapsed + 2))
    done
    
    echo ""
    echo "⚠️  $name (port $port) timeout"
    return 1
}

# ============================================================================
# Main
# ============================================================================

print_header "PHARMACY MANAGEMENT SOA - FULL SYSTEM DEMO"

echo ""
echo "🎯 Demo Scope:"
echo "  Gateway: 1 instance (port 9000)"
echo "  Services: 6 services × 2 instances = 12 instances (ports 9001-9016)"
echo "  Frontend: 1 instance (port 8080)"
echo "  TOTAL: 14 processes"
echo ""

# Step 1: Check prerequisites
print_header "Step 1: Kiểm Tra Điều Kiện Tiên Quyết"

echo ""
echo "Checking prerequisites..."

all_ok=true

check_prerequisite "Java 11+" "java -version" || all_ok=false
check_prerequisite "SBT" "sbt --version" || all_ok=false
check_prerequisite "Node.js" "node --version" || all_ok=false

if [ $SKIP_CONSUL -eq 0 ]; then
    if check_prerequisite "Consul (optional)" "curl -s http://localhost:8500/ui/ | grep -q Consul"; then
        echo "ℹ️  Consul available - will test Service Discovery"
    else
        echo "⚠️  Consul not available - skipping Service Discovery test"
    fi
fi

if [ "$all_ok" = false ]; then
    echo ""
    echo "❌ Some prerequisites missing. Please install required tools."
    exit 1
fi

echo ""
echo "✅ All prerequisites OK!"

# Step 2: Create log directory
mkdir -p "$LOG_DIR"

# Step 3: Start Gateway
print_header "Step 2: Khởi Động API Gateway (Port 9000)"

echo ""
echo "🚀 Starting API Gateway..."

cd "$REPO_ROOT/api-gateway"
sbt "run 9000" > "$LOG_DIR/gateway-1.log" 2>&1 &
GATEWAY_PID=$!

echo "  PID: $GATEWAY_PID"
echo "  Waiting for health check..."

wait_service 9000 "API Gateway" $HEALTH_TIMEOUT

# Step 4: Start Services
print_header "Step 3: Khởi Động 6 Services (12 Instances)"

echo ""

SERVICES=("auth-service" "catalog-service" "inventory-service" "purchase-service" "sales-service" "report-service")
PORTS=(9001 9002 9003 9004 9005 9006)

for i in "${!SERVICES[@]}"; do
    service="${SERVICES[$i]}"
    port="${PORTS[$i]}"
    port2=$((port + 10))
    
    echo "🚀 Starting $service..."
    
    # Instance 1
    echo "  Instance 1 (port $port)..."
    cd "$REPO_ROOT/$service"
    sbt "run $port" > "$LOG_DIR/$service-1.log" 2>&1 &
    PID1=$!
    echo "    PID: $PID1"
    
    wait_service $port "$service-1" $HEALTH_TIMEOUT
    
    # Instance 2
    if [ $INSTANCES -eq 2 ]; then
        echo "  Instance 2 (port $port2)..."
        sleep 2
        sbt -Dservice.instance.id="$service-2" "run $port2" > "$LOG_DIR/$service-2.log" 2>&1 &
        PID2=$!
        echo "    PID: $PID2"
        
        wait_service $port2 "$service-2" $HEALTH_TIMEOUT
    fi
done

# Step 5: Start Frontend
print_header "Step 4: Khởi Động Frontend (Port 8080)"

echo ""
echo "🚀 Starting Frontend..."

cd "$REPO_ROOT/frontend"

if [ ! -d "node_modules" ]; then
    echo "  Installing npm packages..."
    npm install > /dev/null 2>&1
fi

PORT=8080 npm start > "$LOG_DIR/frontend-1.log" 2>&1 &
FRONTEND_PID=$!

echo "  PID: $FRONTEND_PID"
echo "  Waiting for health check..."

wait_service 8080 "Frontend" 30

# Step 6: Summary
print_header "Step 5: Tóm Tắt Hệ Thống"

echo ""
echo "📊 All Services Status:"
echo ""

RUNNING=0
TOTAL=$((1 + (${#SERVICES[@]} * INSTANCES) + 1))

echo "API Gateway:"
if ps -p $GATEWAY_PID > /dev/null 2>&1; then
    echo "  ✅ gateway-1 (9000)"
    RUNNING=$((RUNNING + 1))
else
    echo "  ❌ gateway-1 (9000)"
fi

echo ""
echo "Services:"
for i in "${!SERVICES[@]}"; do
    service="${SERVICES[$i]}"
    port="${PORTS[$i]}"
    port2=$((port + 10))
    
    if netstat -tuln 2>/dev/null | grep -q ":$port "; then
        echo "  ✅ $service-1 ($port)"
        RUNNING=$((RUNNING + 1))
    else
        echo "  ❌ $service-1 ($port)"
    fi
    
    if [ $INSTANCES -eq 2 ]; then
        if netstat -tuln 2>/dev/null | grep -q ":$port2 "; then
            echo "  ✅ $service-2 ($port2)"
            RUNNING=$((RUNNING + 1))
        else
            echo "  ❌ $service-2 ($port2)"
        fi
    fi
done

echo ""
echo "Frontend:"
if ps -p $FRONTEND_PID > /dev/null 2>&1; then
    echo "  ✅ frontend-1 (8080)"
    RUNNING=$((RUNNING + 1))
else
    echo "  ❌ frontend-1 (8080)"
fi

echo ""
echo "════════════════════════════════════════════════════════════════"

echo "📊 Total: $RUNNING / $TOTAL running"

echo ""
echo "🔗 Quick Links:"
echo "  Gateway Health:    http://localhost:9000/health"
echo "  Gateway Metrics:   http://localhost:9000/metrics"
echo "  Consul UI:         http://localhost:8500/ui/"
echo "  Frontend:          http://localhost:8080"

echo ""
echo "📋 Test Commands:"
echo "  Load Balancing:    bash scripts/test-load-balancing.sh"
echo "  Check Health:      bash scripts/check-health.sh"
echo "  View Status:       bash scripts/status.sh --watch"
echo "  Stop All:          bash scripts/stop-all.sh"

echo ""

if [ $RUNNING -eq $TOTAL ]; then
    echo "╔════════════════════════════════════════════════════════════════╗"
    echo "║  ✅ ALL SYSTEMS OPERATIONAL!                                  ║"
    echo "╚════════════════════════════════════════════════════════════════╝"
else
    echo "╔════════════════════════════════════════════════════════════════╗"
    echo "║  ⚠️  SOME SERVICES NOT RESPONDING                             ║"
    echo "║  Check logs in: logs/ folder                                   ║"
    echo "╚════════════════════════════════════════════════════════════════╝"
fi

echo ""
echo "Logs location: $LOG_DIR/"
echo ""
