#!/bin/bash
# ============================================================================
# STATUS.SH - Kiểm Tra Trạng Thái Tất Cả Services
# ============================================================================
# 
# Mục đích: In bảng trạng thái tất cả services
# Hiển thị:
#   - Instance ID
#   - Cổng
#   - PID (process)
#   - Trạng thái (Running/Stopped)
#   - Consul Status (Passing/Critical/Down)
#   - Response Time
# 
# Cách chạy:
#   ./status.sh                    # In bảng status
#   ./status.sh --watch            # Tự động refresh 5 giây
#   ./status.sh --json             # In JSON format
# 
# @author Nhóm 4 - Đại học Quy Nhơn
# ============================================================================

# ============================================================================
# Configuration
# ============================================================================

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPTS_DIR="$REPO_ROOT/scripts"
SERVICES_CONF="$SCRIPTS_DIR/services.conf"
LOG_DIR="$REPO_ROOT/logs"
PID_DIR="$REPO_ROOT/.pids"

WATCH_MODE=0
JSON_MODE=0
WATCH_INTERVAL=5
CONSUL_URL="http://localhost:8500"

# ============================================================================
# Functions
# ============================================================================

# Parse arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --watch)
                WATCH_MODE=1
                shift
                ;;
            --json)
                JSON_MODE=1
                shift
                ;;
            *)
                echo "❌ Unknown option: $1"
                exit 1
                ;;
        esac
    done
}

# In header
print_header() {
    cat << "EOF"
╔════════════════════════════════════════════════════════════════════════════╗
║  PHARMACY MANAGEMENT SOA - STATUS                                         ║
║  Timestamp: $(date '+%Y-%m-%d %H:%M:%S')                                        ║
╚════════════════════════════════════════════════════════════════════════════╝
EOF
}

# Get service status from Consul
get_consul_status() {
    local service_name=$1
    
    local response=$(curl -s "$CONSUL_URL/v1/health/service/$service_name" 2>/dev/null || echo "")
    
    if [ -z "$response" ]; then
        echo "ERROR"
        return
    fi
    
    # Check if any instance is passing
    local passing=$(echo "$response" | grep -o '"Status":"passing"' | wc -l)
    local critical=$(echo "$response" | grep -o '"Status":"critical"' | wc -l)
    
    if [ $passing -gt 0 ]; then
        echo "PASSING"
    elif [ $critical -gt 0 ]; then
        echo "CRITICAL"
    else
        echo "DOWN"
    fi
}

# Get instance status
get_instance_status() {
    local instance_id=$1
    local port=$2
    local pid_file="$PID_DIR/$instance_id.pid"
    
    local process_status="❌ Stopped"
    local pid="—"
    
    if [ -f "$pid_file" ]; then
        pid=$(cat "$pid_file")
        if kill -0 "$pid" 2>/dev/null; then
            process_status="✅ Running"
        else
            process_status="❌ Stopped"
            pid="—"
        fi
    fi
    
    # Try health check
    local health_status="—"
    if [ "$process_status" = "✅ Running" ]; then
        if curl -s "http://localhost:$port/health" > /dev/null 2>&1; then
            health_status="✅"
        else
            health_status="❌"
        fi
    fi
    
    echo "$pid|$process_status|$health_status"
}

# Print status table
print_status_table() {
    echo ""
    printf "%-20s | %-15s | %-8s | %-10s | %-12s | %-10s\n" "Instance ID" "Cổng" "PID" "Process" "Health" "Consul"
    echo "────────────────────┼─────────────────┼──────────┼────────────┼──────────────┼──────────"
    
    while IFS='|' read -r service_name consul_name instance1_id instance1_port instance2_id instance2_port; do
        [[ "$service_name" =~ ^#.*$ ]] && continue
        [[ -z "$service_name" ]] && continue
        
        service_name=$(echo "$service_name" | xargs)
        
        if [ "$service_name" = "CONSUL" ]; then
            continue
        fi
        
        # Instance 1
        instance1_id=$(echo "$instance1_id" | xargs)
        instance1_port=$(echo "$instance1_port" | xargs)
        consul_name=$(echo "$consul_name" | xargs)
        
        if [ -n "$instance1_id" ] && [ "$instance1_id" != "-" ]; then
            local status=$(get_instance_status "$instance1_id" "$instance1_port")
            IFS='|' read -r pid process_status health <<< "$status"
            
            local consul_status="—"
            if [ "$consul_name" != "(không)" ]; then
                consul_status=$(get_consul_status "$consul_name")
            fi
            
            printf "%-20s | %-15s | %-10s | %-12s | %-12s | %-10s\n" \
                "$instance1_id" "$instance1_port" "$pid" "$process_status" "$health" "$consul_status"
        fi
        
        # Instance 2
        instance2_id=$(echo "$instance2_id" | xargs)
        instance2_port=$(echo "$instance2_port" | xargs)
        
        if [ -n "$instance2_id" ] && [ "$instance2_id" != "-" ]; then
            local status=$(get_instance_status "$instance2_id" "$instance2_port")
            IFS='|' read -r pid process_status health <<< "$status"
            
            local consul_status="—"
            if [ "$consul_name" != "(không)" ]; then
                consul_status=$(get_consul_status "$consul_name")
            fi
            
            printf "%-20s | %-15s | %-10s | %-12s | %-12s | %-10s\n" \
                "$instance2_id" "$instance2_port" "$pid" "$process_status" "$health" "$consul_status"
        fi
    done < "$SERVICES_CONF"
}

# Count totals
print_summary() {
    echo ""
    echo "────────────────────────────────────────────────────────────────────────────"
    
    local running=0
    local total=0
    
    for pid_file in "$PID_DIR"/*.pid 2>/dev/null; do
        [ -f "$pid_file" ] || continue
        total=$((total + 1))
        
        local pid=$(cat "$pid_file")
        if kill -0 "$pid" 2>/dev/null; then
            running=$((running + 1))
        fi
    done
    
    if [ $total -eq 0 ]; then
        echo "📊 Tóm tắt: Không có service nào chạy"
    else
        echo "📊 Tóm tắt: $running / $total services đang chạy"
    fi
    
    # Check Consul
    if curl -s "$CONSUL_URL/ui/" > /dev/null 2>&1; then
        echo "✅ Consul: Đang chạy (http://$CONSUL_URL)"
    else
        echo "❌ Consul: Không chạy"
    fi
    
    # Check ports
    local total_ports=0
    while IFS='|' read -r service_name consul_name instance1_id instance1_port instance2_id instance2_port; do
        [[ "$service_name" =~ ^#.*$ ]] && continue
        [[ -z "$service_name" ]] && continue
        
        instance1_port=$(echo "$instance1_port" | xargs)
        instance2_port=$(echo "$instance2_port" | xargs)
        
        if [ -n "$instance1_port" ] && [ "$instance1_port" != "-" ]; then
            total_ports=$((total_ports + 1))
        fi
        
        if [ -n "$instance2_port" ] && [ "$instance2_port" != "-" ]; then
            total_ports=$((total_ports + 1))
        fi
    done < "$SERVICES_CONF"
    
    echo "🔌 Tổng cổng: $total_ports (theo services.conf)"
}

# Print links
print_links() {
    echo ""
    echo "🔗 Links Nhanh:"
    echo "  - Consul UI:       http://localhost:8500/ui/"
    echo "  - Gateway Health:  http://localhost:9000/health"
    echo "  - Frontend:        http://localhost:8080"
    echo ""
}

# Watch mode
watch_mode() {
    while true; do
        clear
        print_header
        print_status_table
        print_summary
        print_links
        
        echo "⏱️  Auto-refresh trong $WATCH_INTERVAL giây (Ctrl+C để thoát)..."
        sleep $WATCH_INTERVAL
    done
}

# JSON output
json_output() {
    local json="{"
    json="$json\"timestamp\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\","
    json="$json\"services\":["
    
    local first=1
    while IFS='|' read -r service_name consul_name instance1_id instance1_port instance2_id instance2_port; do
        [[ "$service_name" =~ ^#.*$ ]] && continue
        [[ -z "$service_name" ]] && continue
        
        service_name=$(echo "$service_name" | xargs)
        
        [ "$first" = 1 ] && first=0 || json="$json,"
        
        json="$json{\"service\":\"$service_name\",\"instances\":[]}"
    done < "$SERVICES_CONF"
    
    json="$json]}"
    echo "$json" | jq .
}

# Main
main() {
    parse_args "$@"
    
    if [ $JSON_MODE -eq 1 ]; then
        json_output
    elif [ $WATCH_MODE -eq 1 ]; then
        watch_mode
    else
        print_header
        print_status_table
        print_summary
        print_links
    fi
}

# ============================================================================
# Entry Point
# ============================================================================

main "$@"
