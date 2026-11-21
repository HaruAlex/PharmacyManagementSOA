# Đợt 6: Structured Logging Setup

## Overview
This document describes the structured logging configuration for the Pharmacy Management SOA system using Logback.

## Log Configuration

### logback.xml Structure
Each service includes a `conf/logback.xml` with:

1. **File Appender**: Logs to rotating files
2. **Console Appender**: Logs to stdout (for container environments)
3. **Logger Configuration**: Per-package log levels

### Key Appenders

#### FileAppender (Rotating)
```xml
<appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
  <file>logs/app.log</file>
  <encoder>
    <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
  </encoder>
  <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
    <fileNamePattern>logs/app-%d{yyyy-MM-dd}.%i.log</fileNamePattern>
    <maxFileSize>10MB</maxFileSize>
    <maxHistory>30</maxHistory>
  </rollingPolicy>
</appender>
```

#### ConsoleAppender
```xml
<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
  <encoder>
    <pattern>%d{HH:mm:ss.SSS} [%-5level] %logger{36} - %msg%n</pattern>
  </encoder>
</appender>
```

### Log Levels

Per package:
- `play.*`: INFO (framework logs)
- `services.*`: DEBUG (service layer tracing)
- `controllers.*`: INFO (HTTP request/response)
- Root: INFO (everything else)

## Structured Logging Usage

### Logger Naming Convention
```java
private final Logger logger = LoggerFactory.getLogger(SomeClass.class);
```

### Log Levels by Scenario

**ERROR**: Unrecoverable errors
```java
Logger.error("[CircuitBreaker] " + name + " transitioning CLOSED → OPEN (failures=" + failures + ")");
```

**WARN**: Degraded state or unusual conditions
```java
Logger.warn("[CircuitBreaker] " + name + " - Task rejected! Active=" + activeCount);
```

**INFO**: Important state changes
```java
Logger.info("[CircuitBreakerRegistry] Creating circuit breaker for: " + serviceName);
```

**DEBUG**: Detailed tracing
```java
Logger.debug("[MetricsCollector] Snapshot captured");
```

## Key Logging Points in Đợt 4-6

### Đợt 4: Fault Tolerance
- Retry attempts and backoff timing
- Idempotency key cache hits/misses
- Request/response headers logged by filter

### Đợt 5: Resilience
- Circuit breaker state transitions (CLOSED → OPEN → HALF_OPEN)
- Failure count increments
- Bulkhead task rejections
- Thread pool statistics

### Đợt 6: Monitoring
- Metrics snapshot captures
- Service registration/deregistration
- Health check invocations

## Log Output Example

```
2026-11-21 16:51:30 [main] INFO  services.CircuitBreakerRegistry - [CircuitBreakerRegistry] Creating circuit breaker for: auth-service
2026-11-21 16:51:30 [http-1] DEBUG services.ResilientServiceCaller - [ResilientServiceCaller] Attempting call to auth-service (attempt 1/3)
2026-11-21 16:51:31 [http-1] DEBUG services.CircuitBreaker - [CircuitBreaker] auth-service recordSuccess (state=CLOSED)
2026-11-21 16:51:31 [http-1] INFO  controllers.MetricsController - [MetricsCollector] Snapshot captured
2026-11-21 16:51:32 [http-2] WARN  services.BulkheadExecutor - [BulkheadExecutor] auth-service - Task rejected! Active=10, Queue=5
```

## Integration with Monitoring

### ELK Stack (Elasticsearch, Logstash, Kibana)

To ship logs to Elasticsearch, add Logstash appender:

```xml
<appender name="LOGSTASH" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
  <destination>logstash-host:5000</destination>
  <encoder class="net.logstash.logback.encoder.LogstashEncoder" />
</appender>
```

Then reference in root logger:
```xml
<root level="INFO">
  <appender-ref ref="CONSOLE" />
  <appender-ref ref="FILE" />
  <appender-ref ref="LOGSTASH" />
</root>
```

### Correlation IDs

For distributed tracing, add to HTTP headers:
```java
String correlationId = UUID.randomUUID().toString();
// Add to MDC for all subsequent logs in this request
MDC.put("correlationId", correlationId);
```

Then in pattern:
```xml
<pattern>%d{HH:mm:ss} [%X{correlationId}] [%thread] %-5level %logger{36} - %msg%n</pattern>
```

## Performance Considerations

1. **Async Appenders**: For high-throughput environments
```xml
<appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">
  <appender-ref ref="FILE" />
</appender>
```

2. **Log Level Filtering**: Avoid debug logs in production
3. **File Rotation**: Set appropriate maxFileSize and maxHistory
4. **Memory**: AsyncAppender uses unbounded queue by default

## Troubleshooting

### Logs Not Appearing
- Check file permissions in logs/ directory
- Verify logback.xml is in conf/ directory
- Check console output for logback initialization errors

### Performance Degradation
- Reduce log level (INFO instead of DEBUG)
- Enable async appenders
- Increase maxFileSize to reduce rotation frequency

### Disk Space
- Monitor logs/ directory size
- Adjust maxHistory based on disk space available
- Consider log shipping to central repository

## Related Documentation
- [Monitoring Setup](./MONITORING-SETUP.md)
- [Logback Official Docs](https://logback.qos.ch/)
- [SLF4J Documentation](https://www.slf4j.org/)
