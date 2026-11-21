#!/bin/bash
# ============================================================================
# STOP-ALL.SH - Dừng Tất Cả Services
# ============================================================================
# 
# Mục đích: Dừng tất cả services một cách graceful
# - Tắt Frontend trước
# - Tắt Gateway
# - Tắt Services (ngược lại thứ tự khởi động)
# 
# Cách chạy:
#   ./stop-all.sh                    # Dừng tất cả
#   ./stop-all.sh inventory-1        # Dừng riêng 1 instance
#   ./stop-all.sh --force            # Dừng ngay (kill -9)
# 
# PID: Đọc từ .pids/<instance-id>.pid
# Log: Ghi vào logs/<instance-id>.log
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

FORCE_KILL=0
SINGLE_INSTANCE=""
GRACEFUL_TIMEOUT=10

# ============================================================================
# Functions
# ============================================================================

# In header
print_header() {
    cat << "EOF"
╔════════════════════════════════════════════════════════════════╗
║  PHARMACY MANAGEMENT SOA - STOP ALL                           ║
╚════════════════════════════════════════════════════════════════╝
EOF
}

# Parse arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --force)
                FORCE_KILL=1
                shift
                ;;
            *)
                SINGLE_INSTANCE="$1"
                shift
                ;;
        esac
    done
}

# Stop single service
stop_service() {
    local instance_id=$1
    local pid_file="$PID_DIR/$instance_id.pid"
    
    if [ ! -f "$pid_file" ]; then
        echo "⚠️  $instance_id: PID file không tồn tại (có thể chưa chạy)"
        return 0
    fi
    
    local pid=$(cat "$pid_file")
    
    if ! kill -0 "$pid" 2>/dev/null; then
        echo "⚠️  $instance_id (PID $pid): Process không tồn tại (đã tắt?)"
        rm -f "$pid_file"
        return 0
    fi
    
    echo "🛑 Dừng: $instance_id (PID $pid)"
    
    if [ $FORCE_KILL -eq 1 ]; then
        # Kill ngay
        kill -9 "$pid" 2>/dev/null || true
        echo "  ⚡ Killed (force)"
    else
        # Graceful shutdown
        kill -SIGTERM "$pid" 2>/dev/null || true
        
        # Chờ process tắt
        local elapsed=0
        while kill -0 "$pid" 2>/dev/null && [ $elapsed -lt $GRACEFUL_TIMEOUT ]; do
            sleep 1
            elapsed=$((elapsed + 1))
            echo -n "."
        done
        
        # Nếu vẫn chạy, kill -9
        if kill -0 "$pid" 2>/dev/null; then
            echo ""
            echo "  ⚠️  Process vẫn chạy, force kill..."
            kill -9 "$pid" 2>/dev/null || true
        else
            echo ""
            echo "  ✅ Đã tắt gracefully"
        fi
    fi
    
    rm -f "$pid_file"
}

# Stop all services
stop_all_services() {
    echo ""
    echo "Dừng Services..."
    echo "=================================================="
    
    # Nếu chỉ định instance, dừng riêng cái đó
    if [ -n "$SINGLE_INSTANCE" ]; then
        stop_service "$SINGLE_INSTANCE"
        return
    fi
    
    # Nếu không, dừng theo thứ tự ngược:
    # 1. Frontend
    # 2. Gateway
    # 3. Services
    
    # Stop Frontend
    if [ -f "$PID_DIR/frontend-1.pid" ]; then
        stop_service "frontend-1"
    fi
    
    sleep 1
    
    # Stop Gateway
    if [ -f "$PID_DIR/gateway-1.pid" ]; then
        stop_service "gateway-1"
    fi
    
    sleep 1
    
    # Stop Services (tất cả instances)
    # Đọc services.conf theo thứ tự ngược
    local services_array=()
    while IFS='|' read -r service_name consul_name instance1_id instance1_port instance2_id instance2_port; do
        [[ "$service_name" =~ ^#.*$ ]] && continue
        [[ -z "$service_name" ]] && continue
        
        service_name=$(echo "$service_name" | xargs)
        
        if [ "$service_name" != "GATEWAY" ] && [ "$service_name" != "FRONTEND" ] && [ "$service_name" != "CONSUL" ]; then
            instance1_id=$(echo "$instance1_id" | xargs)
            instance2_id=$(echo "$instance2_id" | xargs)
            
            services_array+=("$instance1_id" "$instance2_id")
        fi
    done < "$SERVICES_CONF"
    
    # Dừng theo thứ tự ngược
    for ((i=${#services_array[@]}-1; i>=0; i--)); do
        instance_id="${services_array[$i]}"
        if [ -n "$instance_id" ] && [ "$instance_id" != "-" ]; then
            stop_service "$instance_id" || true
        fi
    done
}

# Verify all stopped
verify_stopped() {
    echo ""
    echo "📋 Kiểm tra các process đã tắt..."
    echo "=================================================="
    
    local running=0
    for pid_file in "$PID_DIR"/*.pid; do
        if [ -f "$pid_file" ]; then
            local pid=$(cat "$pid_file")
            if kill -0 "$pid" 2>/dev/null; then
                local instance_id=$(basename "$pid_file" .pid)
                echo "⚠️  $instance_id (PID $pid) vẫn còn chạy!"
                running=$((running + 1))
            fi
        fi
    done
    
    if [ $running -eq 0 ]; then
        echo "✅ Tất cả process đã tắt"
        
        # Clean up PID files
        rm -f "$PID_DIR"/*.pid
        rmdir "$PID_DIR" 2>/dev/null || true
        
        return 0
    else
        echo "❌ Còn $running process(s) chạy"
        return 1
    fi
}

# Main
main() {
    print_header
    parse_args "$@"
    
    if [ -n "$SINGLE_INSTANCE" ]; then
        echo "Dừng instance: $SINGLE_INSTANCE"
    else
        echo "Dừng tất cả services"
    fi
    
    echo ""
    stop_all_services
    verify_stopped
    
    echo ""
    echo "✅ Dừng hoàn tất!"
    echo ""
    echo "Để khởi động lại: ./start-all.sh"
}

# ============================================================================
# Entry Point
# ============================================================================

main "$@"
