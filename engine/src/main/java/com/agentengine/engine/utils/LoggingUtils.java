package com.agentengine.engine.utils;

import org.slf4j.MDC;
import java.util.UUID;

/**
 * Utility class for structured logging and MDC (Mapped Diagnostic Context) management.
 */
public class LoggingUtils {

    public static final String TRACE_ID = "trace_id";

    /**
     * Generates a new trace ID if one doesn't exist in the current MDC context.
     * This ensures that all log entries within a request/response cycle share the same trace ID.
     */
    public static String getOrCreateTraceId() {
        String traceId = MDC.get(TRACE_ID);
        if (traceId == null || traceId.isEmpty()) {
            traceId = generateTraceId();
            MDC.put(TRACE_ID, traceId);
        }
        return traceId;
    }

    /**
     * Generates a new random trace ID.
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString();
    }
}