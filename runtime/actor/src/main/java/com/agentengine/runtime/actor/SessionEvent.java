package com.agentengine.runtime.actor;

import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;

import java.util.Map;

/**
 * Event emitted by a session actor during execution.
 * <p>
 * The {@code sequence} field provides per-session ordering guarantees:
 * events from the same {@code sessionId} are totally ordered by sequence number.
 * Events across different sessions have no ordering guarantee.
 */
public record SessionEvent(
        String id,
    String rootSessionId,
    String parentSessionId,
    String sessionId,
    String runId,
    String author,
    Content content,
    Boolean partial,
    Boolean turnComplete,
    FinishReason finishReason,
    long timestamp,
    long sequence, Map<String, Object> metadata
) {

    public SessionEvent withSequence(long sequence) {
        return new SessionEvent(id,
            rootSessionId, parentSessionId, sessionId, runId, author,
            content, partial, turnComplete, finishReason, timestamp, sequence, metadata
        );
    }

    public SessionEvent withContent(Content content) {
        return new SessionEvent(id,
                rootSessionId, parentSessionId, sessionId, runId, author,
            content, partial, turnComplete, finishReason, timestamp, sequence, metadata
        );
    }

    public SessionEvent withTurnComplete(boolean turnComplete) {
        return new SessionEvent(id,
                rootSessionId, parentSessionId, sessionId, runId, author,
            content, partial, turnComplete, finishReason, timestamp, sequence, metadata
        );
    }

    public SessionEvent withMetadata(Map<String, Object> metadata) {
        return new SessionEvent(id,
                rootSessionId, parentSessionId, sessionId, runId, author,
                content, partial, turnComplete, finishReason, timestamp, sequence, metadata
        );
    }
}
