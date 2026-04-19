package com.agentengine.util.agents.beans;

import com.agentengine.util.common.beans.BaseEntity;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Event emitted by a session actor during execution.
 *
 * <p>The {@code sequence} field provides per-session ordering guarantees: events from the same
 * {@code sessionId} are totally ordered by sequence number. Events across different sessions have
 * no ordering guarantee.
 */
public final class SessionEvent extends BaseEntity {
    public static final String AUTHOR_USER = "user";
    public static final String FIELD_SESSION_ID = "sessionId";

    public enum Type {
        UNKNOWN,
        NORMAL,
        ERROR,
        TERMINAL,
        LIVE_MARKER;

        public static Type valueOfOrDefault(final String value) {
            if (value == null) {
                return UNKNOWN;
            }
            try {
                return valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return UNKNOWN;
            }
        }
    }

    private String rootSessionId;
    private String parentSessionId;
    private String sessionId;
    private String runId;
    private String author;
    private Content content;
    private Boolean partial;
    private Boolean turnComplete;
    private FinishReason finishReason;
    private long timestamp;
    private long sequence;
    private Map<String, Object> metadata;
    private Type type = Type.NORMAL;
    private String errorMessage;

    public SessionEvent() {}

    public SessionEvent(
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
            long sequence,
            Map<String, Object> metadata,
            final Type type) {
        setId(id);
        this.rootSessionId = rootSessionId;
        this.parentSessionId = parentSessionId;
        this.sessionId = sessionId;
        this.runId = runId;
        this.author = author;
        this.content = content;
        this.partial = partial;
        this.turnComplete = turnComplete;
        this.finishReason = finishReason;
        this.timestamp = timestamp;
        this.sequence = sequence;
        this.metadata = metadata;
        this.type = type;
    }

    public String getRootSessionId() {
        return rootSessionId;
    }

    public String getParentSessionId() {
        return parentSessionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getRunId() {
        return runId;
    }

    public String getAuthor() {
        return author;
    }

    public Content getContent() {
        return content;
    }

    public Boolean isPartial() {
        return partial;
    }

    public Boolean isTurnComplete() {
        return turnComplete;
    }

    public FinishReason getFinishReason() {
        return finishReason;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getSequence() {
        return sequence;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Type getType() {
        return type != null ? type : Type.NORMAL;
    }

    public boolean isTerminal() {
        return getType() == Type.TERMINAL;
    }

    public boolean isLiveMarker() {
        return getType() == Type.LIVE_MARKER;
    }

    public boolean isError() {
        return getType() == Type.ERROR;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(final String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Boolean getTurnComplete() {
        return turnComplete;
    }

    public Boolean getPartial() {
        return partial;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        final SessionEvent that = (SessionEvent) obj;
        return Objects.equals(this.getId(), that.getId())
                && Objects.equals(this.rootSessionId, that.rootSessionId)
                && Objects.equals(this.parentSessionId, that.parentSessionId)
                && Objects.equals(this.sessionId, that.sessionId)
                && Objects.equals(this.runId, that.runId)
                && Objects.equals(this.author, that.author)
                && Objects.equals(this.content, that.content)
                && Objects.equals(this.partial, that.partial)
                && Objects.equals(this.turnComplete, that.turnComplete)
                && Objects.equals(this.finishReason, that.finishReason)
                && this.timestamp == that.timestamp
                && this.sequence == that.sequence
                && Objects.equals(this.metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getId(),
                rootSessionId,
                parentSessionId,
                sessionId,
                runId,
                author,
                content,
                partial,
                turnComplete,
                finishReason,
                timestamp,
                sequence,
                metadata);
    }

    @Override
    public String toString() {
        return "SessionEvent["
                + "id=" + getId()
                + ", rootSessionId=" + rootSessionId
                + ", parentSessionId=" + parentSessionId
                + ", sessionId=" + sessionId
                + ", runId=" + runId
                + ", author=" + author
                + ", content=" + content
                + ", partial=" + partial
                + ", turnComplete=" + turnComplete
                + ", finishReason=" + finishReason
                + ", timestamp=" + timestamp
                + ", type=" + type
                + ", errorMessage=" + errorMessage
                + ", sequence=" + sequence
                + ", metadata=" + metadata
                + ']';
    }

    public void setRootSessionId(final String rootSessionId) {
        this.rootSessionId = rootSessionId;
    }

    public void setParentSessionId(final String parentSessionId) {
        this.parentSessionId = parentSessionId;
    }

    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    public void setRunId(final String runId) {
        this.runId = runId;
    }

    public void setAuthor(final String author) {
        this.author = author;
    }

    public void setContent(final Content content) {
        this.content = content;
    }

    public void setPartial(final Boolean partial) {
        this.partial = partial;
    }

    public void setTurnComplete(final Boolean turnComplete) {
        this.turnComplete = turnComplete;
    }

    public void setFinishReason(final FinishReason finishReason) {
        this.finishReason = finishReason;
    }

    public void setTimestamp(final long timestamp) {
        this.timestamp = timestamp;
    }

    public void setSequence(final long sequence) {
        this.sequence = sequence;
    }

    public void setMetadata(final Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public static SessionEvent liveMarker(final String sessionId) {
        final SessionEvent event = new SessionEvent();
        event.setSessionId(sessionId);
        event.type = Type.LIVE_MARKER;
        return event;
    }

    public static SessionEvent terminal(final String sessionId) {
        return new SessionEvent(
                UUID.randomUUID().toString(),
                null,
                null,
                sessionId,
                null,
                null,
                null,
                null,
                null,
                null,
                System.currentTimeMillis(),
                Long.MAX_VALUE,
                null,
                Type.TERMINAL);
    }

    /**
     * Creates an error event for a failed session run.
     *
     * <p>Emitted before the terminal event so the AGUI mapper can translate it to a
     * {@code RunErrorEvent}. The {@code sequence} places the error at the correct position
     * relative to the turn events that preceded the failure.
     *
     * <p>For live broadcast (where ordering is real-time), pass {@code Long.MAX_VALUE - 1}.
     * For history replay, pass the next available sequence counter so the error sorts
     * immediately after the last event from the failed turn.
     */
    public static SessionEvent error(
            final String rootSessionId, final String sessionId, final String errorMessage, final long sequence) {
        final SessionEvent event = new SessionEvent(
                UUID.randomUUID().toString(),
                rootSessionId,
                null,
                sessionId,
                null,
                null,
                null,
                null,
                null,
                null,
                System.currentTimeMillis(),
                sequence,
                null,
                Type.ERROR);
        event.errorMessage = errorMessage;
        return event;
    }
}
