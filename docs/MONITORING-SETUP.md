# Đợt 6: Monitoring & Observability Setup

## Overview
This document describes the monitoring and observability infrastructure for the Pharmacy Management SOA system.

## Endpoints

### 1. GET `/health` - Simple Health Check
Returns basic health status.

**Response (200 OK):**
```json
{
  "status": "UP",
  "service": "api-gateway"
}
```

### 2. GET `/healthz` - Detailed Health Check (Kubernetes-style)
Returns comprehensive health information including JVM metrics, circuit breaker status, and bulkhead metrics.

**Response (200 OK):**
```json
{
  "status": "UP",
  "service": "api-gateway",
  "timestamp": "2026-11-21T16:51:40Z",
  "uptime_ms": 125000,
  "jvm": {
    "status": "UP",
    "heap_used_mb": 256,
    "heap_max_mb": 1024,
    "threads": 42,
    "peak_threads": 45
  },
  "circuitBreakers": {
    "status": "UP",
    "total": 6,
    "open": 0,
    "details": [
      {
        "service": "auth-service",
        "state": "CLOSED",
        "failures": 0
      }
    ]
  },
  "bulkheads": {
    "status": "UP",
    "total": 6,
    "details": [
      {
        "service": "auth-service",
        "active_threads": 3,
        "queue_size": 1,
        "rejected_tasks": 0
      }
    ]
  }
}
```

### 3. GET `/metrics` - Prometheus Format
Exposes metrics in Prometheus text format for scraping.

**Response (200 OK):**
```
# HELP circuit_breaker_state Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
# TYPE circuit_breaker_state gauge
circuit_breaker_state{service="auth-service"} 0
circuit_breaker_state{service="catalog-service"} 0

# HELP circuit_breaker_failures Total failures recorded
# TYPE circuit_breaker_failures counter
circuit_breaker_failures{service="auth-service"} 0

# HELP bulkhead_active_threads Active threads in bulkhead pool
# TYPE bulkhead_active_threads gauge
bulkhead_active_threads{service="auth-service"} 3

# HELP bulkhead_queue_size Current queue size
# TYPE bulkhead_queue_size gauge
bulkhead_queue_size{service="auth-service"} 1

# HELP bulkhead_rejected_tasks Total rejected tasks
# TYPE bulkhead_rejected_tasks counter
bulkhead_rejected_tasks{service="auth-service"} 0

# HELP jvm_memory_used_bytes JVM memory used
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{type="heap"} 268435456

# HELP jvm_memory_max_bytes JVM max memory
# TYPE jvm_memory_max_bytes gauge
jvm_memory_max_bytes{type="heap"} 1073741824

# HELP jvm_threads_current Current active threads
# TYPE jvm_threads_current gauge
jvm_threads_current 42

# HELP uptime_ms Application uptime in milliseconds
# TYPE uptime_ms gauge
uptime_ms 125000
```

## Metrics Collected

### Circuit Breaker Metrics
- **circuit_breaker_state**: Current state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
- **circuit_breaker_failures**: Total failures recorded per service

### Bulkhead Metrics
- **bulkhead_active_threads**: Number of active threads in pool
- **bulkhead_queue_size**: Current size of task queue
- **bulkhead_rejected_tasks**: Total tasks rejected due to full queue

### JVM Metrics
- **jvm_memory_used_bytes**: Heap memory currently used
- **jvm_memory_max_bytes**: Maximum heap memory available
- **jvm_threads_current**: Currently active threads
- **uptime_ms**: Application uptime in milliseconds

## Architecture

### MetricsCollector
- Singleton service that collects metrics from:
  - CircuitBreakerRegistry (all circuit breakers)
  - BulkheadRegistry (all bulkheads)
  - JVM Runtime
- Captures snapshots on demand for both JSON and Prometheus formats

### MetricsController
- Exposes three HTTP endpoints
- Delegates to MetricsCollector for data collection
- Handles content negotiation (text/plain for Prometheus)

### Registries
- **CircuitBreakerRegistry**: Maintains all circuit breaker instances, provides getAllCircuitBreakers()
- **BulkheadRegistry**: Maintains all bulkhead executor instances, provides getAllBulkheads()

## Integration with Prometheus

To scrape metrics from this gateway:

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'pharmacy-gateway'
    static_configs:
      - targets: ['localhost:9000']
    metrics_path: '/metrics'
    scrape_interval: 15s
```

## Grafana Dashboards

Key metrics to display:
1. **Circuit Breaker Status**: Shows all services and their CB state
2. **Bulkhead Utilization**: Active threads vs. max threads per service
3. **JVM Health**: Memory usage, thread count, uptime
4. **Error Rates**: Failures per circuit breaker

## Alerting Rules

Suggested alerts:

```yaml
# Alert when circuit breaker is OPEN
- alert: CircuitBreakerOpen
  expr: circuit_breaker_state > 0
  for: 1m
  annotations:
    summary: "Circuit breaker open for {{ $labels.service }}"

# Alert when bulkhead queue is near full
- alert: BulkheadQueueFull
  expr: bulkhead_queue_size > 4
  for: 1m
  annotations:
    summary: "Bulkhead queue near capacity for {{ $labels.service }}"

# Alert on high memory usage
- alert: HighMemoryUsage
  expr: jvm_memory_used_bytes / jvm_memory_max_bytes > 0.85
  for: 5m
  annotations:
    summary: "JVM memory usage above 85%"
```

## Testing Endpoints

```bash
# Simple health check
curl http://localhost:9000/health

# Detailed health check
curl http://localhost:9000/healthz

# Prometheus metrics
curl http://localhost:9000/metrics

# Pretty print JSON
curl http://localhost:9000/healthz | jq .
```

## Production Considerations

1. **Metrics Retention**: Configure Prometheus to retain metrics for appropriate time window
2. **Scrape Frequency**: Adjust based on monitoring needs (default: 15s)
3. **Alert Thresholds**: Tune thresholds based on actual system behavior
4. **Dashboard Customization**: Create dashboards specific to your SLOs
5. **Log Aggregation**: Combine with structured logging (Đợt 6 phase)

## Related Documentation
- [Logging Setup](./LOGGING-SETUP.md)
- [Circuit Breaker Details](./dot-5.md)
- [Bulkhead Pattern](./dot-5.md)
