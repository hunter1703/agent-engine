package com.agentengine.runtime.projection;

import com.agentengine.runtime.actor.SessionEvent;

/**
 * MongoDB document representing a single committed session event. Stored in the
 * {@code session_events} collection, keyed by (sessionId, sequence).
 *
 * <p>
 * Unique index on (sessionId, sequence) ensures idempotent projection writes.
 * Query index on (sessionId) sorted by sequence enables efficient replay reads.
 */
public record SessionEventRecord(String sessionId, long sequence, SessionEvent event) {
}
