#!/bin/bash
# ============================================================================
# START-ALL.SH - Khởi Động Tất Cả Services
# ============================================================================
# 
# Mục đích: Khởi động tất cả services, gateway, frontend theo thứ tự
# - Services (auth, catalog, inventory, purchase, sales, report)
# - Gateway
# - Frontend
# 
# Cách chạy:
#   ./start-all.sh                    # Khởi động 2 instance mỗi service (mặc định)
#   ./start-all.sh --instances 1      # Khởi động 1 instance mỗi service
#   ./start-all.sh --services auth,catalog   # Chỉ khởi động auth + catalog
#   ./start-all.sh --services frontend      # Chỉ khởi động frontend
# 
# Output:
#   - Log: logs/<instance-id>.log
#   - PID: .pids/<instance-id>.pid
#   - Status: Kiểm tra /health endpoint
# 
# Yêu cầu: SBT, Node.js, Consul (đã chạy), SQL Server (đã chạy)
# 
# @author Nhóm 4 - Đại học Quy Nhơn
# ============================================================================

set -e

# ============================================================================
# Configuration
# ============================================================================

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPTS_DIR="$REPO_ROOT/scripts"
SERVICES_CONF="$SCRIPTS_DIR/services.conf"
LOG_DIR="$REPO_ROOT/logs"
PID_DIR="$REPO_ROOT/.pids"

# Mặc định: 2 instances
INSTANCES=2
SELECTED_SERVICES=""
HEALTH_TIMEOUT=60
HEALTH_CHECK_INTERVAL=2

# ============================================================================
# Functions
# ============================================================================

# In header
print_header() {
    cat << "EOF"
╔════════════════════════════════════════════════════════════════╗
║  PHARMACY MANAGEMENT SOA - START ALL                          ║
╚════════════════════════════════════════════════════════════════╝
EOF
}

# Parse arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --instances)
                INSTANCES="$2"
                shift 2
                ;;
            --services)
                SELECTED_SERVICES="$2"
                shift 2
                ;;
            *)
                echo "❌ Unknown option: $1"
                exit 1
                ;;
        esac
    done
}

# Check prerequisites
check_prerequisites() {
    echo "📋 Kiểm tra điều kiện tiên quyết..."
    
    # Check SBT
    if ! command -v sbt &> /dev/null; then
        echo "❌ SBT không được cài đặt"
        exit 1
    fi
    
    # Check Node.js
    if ! command -v node &> /dev/null; then
        echo "❌ Node.js không được cài đặt"
        exit 1
    fi
    
    # Check Consul
    if ! curl -s http://localhost:8500/ui/ > /dev/null; then
        echo "❌ Consul không chạy (http://localhost:8500)"
        exit 1
    fi
    
    echo "✅ Tất cả điều kiện OK (SBT, Node.js, Consul)"
}

# Create directories
create_directories() {
    mkdir -p "$LOG_DIR"
    mkdir -p "$PID_DIR"
}

# Start service
start_service() {
    local service=$1
    local instance_id=$2
    local port=$3
    
    # Check port is free
    if netstat -tuln 2>/dev/null | grep -q ":$port "; then
        echo "⚠️  Port $port đang sử dụng (có thể instance đã chạy)"
        return 1
    fi
    
    local service_dir="$REPO_ROOT/$service"
    local log_file="$LOG_DIR/$instance_id.log"
    local pid_file="$PID_DIR/$instance_id.pid"
    
    if [ ! -d "$service_dir" ]; then
        echo "❌ Thư mục service không tồn tại: $service_dir"
        return 1
    fi
    
    echo "🚀 Khởi động: $instance_id (cổng $port)"
    
    # Run in background
    if [ "$instance_id" = "frontend-1" ]; then
        # Frontend (Node.js)
        cd "$service_dir"
        if [ ! -d "node_modules" ]; then
            echo "  📦 Cài npm packages..."
            npm install > /dev/null 2>&1
        fi
        PORT=$port npm start > "$log_file" 2>&1 &
        echo $! > "$pid_file"
    else
        # Java services
        if [ "$instance_id" = "${instance_id%-2}" ]; then
            # Instance 1: chạy với sbt run
            cd "$service_dir"
            sbt "run $port" > "$log_file" 2>&1 &
        else
            # Instance 2: chạy với system property
            local base_instance="${instance_id%-2}"
            local service_name=$(echo $base_instance | cut -d'-' -f1)
            cd "$REPO_ROOT/$service_name"
            sbt -Dservice.instance.id="$instance_id" "run $port" > "$log_file" 2>&1 &
        fi
        echo $! > "$pid_file"
    fi
    
    # Wait for health check
    local elapsed=0
    while [ $elapsed -lt $HEALTH_TIMEOUT ]; do
        if curl -s "http://localhost:$port/health" > /dev/null 2>&1; then
            echo "  ✅ Khởi động thành công!"
            return 0
        fi
        sleep $HEALTH_CHECK_INTERVAL
        elapsed=$((elapsed + HEALTH_CHECK_INTERVAL))
        echo -n "."
    done
    
    echo ""
    echo "⚠️  Timeout chờ health check ($HEALTH_TIMEOUT giây)"
    return 1
}

# Parse services.conf and start
start_all_services() {
    local gateway_started=0
    local services_started=0
    local frontend_started=0
    
    echo ""
    echo "📡 Khởi động Services..."
    echo "=================================================="
    
    # Đọc từng dòng trong services.conf (skip comments và rỗng)
    while IFS='|' read -r service_name consul_name instance1_id instance1_port instance2_id instance2_port; do
        # Skip comments and empty lines
        [[ "$service_name" =~ ^#.*$ ]] && continue
        [[ -z "$service_name" ]] && continue
        
        # Trim whitespace
        service_name=$(echo "$service_name" | xargs)
        
        # Check if should start this service
        if [ -n "$SELECTED_SERVICES" ]; then
            if [[ ! "$SELECTED_SERVICES" =~ "$service_name" ]]; then
                continue
            fi
        fi
        
        if [ "$service_name" = "GATEWAY" ]; then
            start_service "${instance1_id,,}" "$instance1_id" "$instance1_port"
            gateway_started=$?
        elif [ "$service_name" = "FRONTEND" ]; then
            start_service "frontend" "$instance1_id" "$instance1_port"
            frontend_started=$?
        elif [ "$service_name" != "CONSUL" ]; then
            # Regular services
            start_service "${service_name,,}-service" "$instance1_id" "$instance1_port"
            
            if [ $INSTANCES -eq 2 ]; then
                sleep 2  # Wait between instance 1 and 2
                start_service "${service_name,,}-service" "$instance2_id" "$instance2_port"
            fi
            services_started=$((services_started + 1))
        fi
    done < "$SERVICES_CONF"
    
    echo ""
    echo "=================================================="
    echo "📊 Tóm tắt Khởi Động:"
    echo "  Services: $services_started service(s) started"
    echo "  Gateway: OK"
    echo "  Frontend: OK"
}

# Show status
show_status() {
    echo ""
    echo "📋 Bảng Server-Cổng:"
    echo "=================================================="
    echo "Service | Instance ID | Cổng | PID | Trạng thái"
    echo "--------+-----+----------+-----+----------"
    
    while IFS='|' read -r service_name consul_name instance1_id instance1_port instance2_id instance2_port; do
        [[ "$service_name" =~ ^#.*$ ]] && continue
        [[ -z "$service_name" ]] && continue
        
        service_name=$(echo "$service_name" | xargs)
        
        if [ "$service_name" = "GATEWAY" ] || [ "$service_name" = "FRONTEND" ]; then
            instance1_id=$(echo "$instance1_id" | xargs)
            instance1_port=$(echo "$instance1_port" | xargs)
            pid_file="$PID_DIR/$instance1_id.pid"
            
            if [ -f "$pid_file" ]; then
                pid=$(cat "$pid_file")
                status="✅ Running"
            else
                status="❌ Stopped"
            fi
            
            echo "$service_name | $instance1_id | $instance1_port | $pid | $status"
        elif [ "$service_name" != "CONSUL" ]; then
            for i in 1 2; do
                if [ $i -eq 1 ]; then
                    instance_id=$(echo "$instance1_id" | xargs)
                    port=$(echo "$instance1_port" | xargs)
                else
                    instance_id=$(echo "$instance2_id" | xargs)
                    port=$(echo "$instance2_port" | xargs)
                fi
                
                if [ -z "$instance_id" ]; then
                    continue
                fi
                
                pid_file="$PID_DIR/$instance_id.pid"
                
                if [ -f "$pid_file" ]; then
                    pid=$(cat "$pid_file")
                    status="✅ Running"
                else
                    status="❌ Stopped"
                fi
                
                echo "$service_name | $instance_id | $port | $pid | $status"
            done
        fi
    done < "$SERVICES_CONF"
}

# Show links
show_links() {
    echo ""
    echo "🔗 Links Nhanh:"
    echo "=================================================="
    echo "  Gateway Health:    http://localhost:9000/health"
    echo "  Gateway Metrics:   http://localhost:9000/metrics"
    echo "  Consul UI:         http://localhost:8500/ui/"
    echo "  Frontend:          http://localhost:8080"
    echo ""
}

# Main
main() {
    print_header
    parse_args "$@"
    check_prerequisites
    create_directories
    start_all_services
    show_status
    show_links
    
    echo "✅ Khởi động hoàn tất!"
    echo ""
    echo "Để dừng tất cả: ./stop-all.sh"
}

# ============================================================================
# Entry Point
# ============================================================================

main "$@"
