# Logging Guide

This document describes the logging standards and conventions for the Agent Engine project.

## Overview

The project uses SLF4J with Logback as the logging implementation. All log entries follow a structured format with standardized field names to enable efficient debugging and monitoring in production environments.

The logging approach focuses on key lifecycle events and error conditions while avoiding excessive timing logs that could create noise in production systems.

## Field Naming Conventions

### Common Fields
- `trace_id`: Unique identifier for the request/correlation context (UUID)
- `operation`: Name of the operation being performed (e.g., "agent.invoke", "config.load")
- `outcome`: Result of the operation ("success", "failure", "error")

### Request-Specific Fields
- `session_id`: Session identifier
- `agent_id`: Agent identifier
- `run_id`: Agent execution run identifier

## Log Levels Policy

- `ERROR`: Unhandled exceptions, system failures that require immediate attention
  ```java
  LOG.error("Agent invocation failed - trace_id={} agent_id={} outcome=failure error=\"{}\"",
            traceId, agentId, e.getMessage(), e);
  ```

- `WARN`: Recoverable issues, deprecated usage, unexpected but handled conditions
  ```java
  LOG.warn("Agent configuration not found - agent_id={} config_path=\"{}\"", agentId, configPath);
  ```

- `INFO`: Key lifecycle events, successful operations, important state changes
  ```java
  LOG.info("Agent invocation completed - trace_id={} agent_id={} outcome=success",
           traceId, agentId);
  ```

- `DEBUG`: Detailed flow information, variable states for troubleshooting
  ```java
  LOG.debug("New session created - session_id={}", newSessionId);
  ```

- `TRACE`: Very detailed information, method entry/exit, full object dumps (used sparingly)

## Using Structured Logging

### Basic Structured Logging
```java
LOG.info("Agent invocation started - trace_id={} agent_id={} session_id={}", 
         traceId, request.getAgentId(), request.getSessionId());
```

### With Trace ID
```java
// Generate or retrieve trace ID for this request
String traceId = LoggingUtils.getOrCreateTraceId();

// Log with trace ID included explicitly
LOG.info("Processing agent request - trace_id={} agent_id={} session_id={}",
         traceId, request.getAgentId(), request.getSessionId());
```

### Error Logging with Stack Traces
```java
try {
    // some operation
} catch (Exception e) {
    LOG.error("Operation failed - trace_id={} agent_id={} error=\"{}\"",
              traceId, agentId, e.getMessage(), e); // Note: exception at end
    throw e;
}
```

## Correlation ID Propagation

The system uses trace IDs for request correlation. The `LoggingUtils` class helps manage trace IDs:

```java
// At request entry point
String traceId = LoggingUtils.getOrCreateTraceId();

// Include trace_id in log statements explicitly
LOG.info("Processing request - trace_id={} ...", traceId, ...);
```

## Configuration

The logging configuration is defined in `logback.xml` and outputs structured JSON logs with all MDC fields included.

### Development Configuration
The REST module defaults to `ERROR` level logging for all packages, with `DEBUG` enabled only for
`com.agentengine.*`. To adjust the defaults locally, update `application.properties`:
```properties
quarkus.log.level=ERROR
quarkus.log.category."com.agentengine".level=DEBUG
```

## Best Practices

1. **Always use parameterized logging** to avoid expensive string concatenation
2. **Include context in every log** - at minimum trace_id, operation, and component
3. **Don't log sensitive information** like passwords, tokens, or PII
4. **Use consistent field names** across the codebase
5. **Log at the appropriate level** - don't put important operational info in DEBUG
6. **Include duration for timed operations** to enable performance monitoring
7. **Clear MDC context** when leaving request scope to prevent cross-contamination
