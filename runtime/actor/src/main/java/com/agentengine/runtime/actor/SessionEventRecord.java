package com.agentengine.runtime.actor;

/** MongoDB document written by SessionHistoryProjectionHandler. */
public record SessionEventRecord(String sessionId, long sequence, String eventJson) {}
