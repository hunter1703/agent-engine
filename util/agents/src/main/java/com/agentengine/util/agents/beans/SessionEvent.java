package com.agentengine.util.agents.beans;

import com.agentengine.util.common.annotations.Index;
import com.agentengine.util.common.beans.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.adk.events.Event;
import com.google.adk.sessions.State;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bson.codecs.pojo.annotations.BsonIgnore;

/**
 * Event emitted by a session actor during execution.
 *
 * <p>{@code sequence} orders events within one session; not comparable across sessions.
 */
@Index(name = "session_events_turn_idx", def = "{'sessionId': 1, 'turnId': 1, 'sequence': 1}")
public final class SessionEvent extends BaseEntity {
  public static final String FIELD_SESSION_ID = "sessionId";
  public static final String FIELD_ROOT_SESSION_ID = "rootSessionId";
  public static final String FIELD_TURN_ID = "turnId";
  public static final String FIELD_SEQUENCE = "sequence";

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
  private long sequence;
  private Type type = Type.NORMAL;
  private String turnId;
  private String rawEventJson;
  @BsonIgnore @JsonIgnore private Event rawEvent;

  public SessionEvent() {}

  public SessionEvent(
      final String id,
      final String rootSessionId,
      final String parentSessionId,
      final String sessionId,
      final long sequence,
      final Type type,
      final String turnId,
      final Event rawEvent) {
    setId(id);
    this.rootSessionId = rootSessionId;
    this.parentSessionId = parentSessionId;
    this.sessionId = sessionId;
    this.sequence = sequence;
    this.type = type;
    this.turnId = turnId;
    this.rawEvent = rawEvent;
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
    return rawEvent.invocationId();
  }

  public String getAuthor() {
    return rawEvent.author();
  }

  public Content getContent() {
    return rawEvent.content().orElse(null);
  }

  public Boolean isPartial() {
    return rawEvent.partial().orElse(false);
  }

  public Boolean isTurnComplete() {
    return rawEvent.turnComplete().orElse(false);
  }

  public FinishReason getFinishReason() {
    return rawEvent.finishReason().orElse(null);
  }

  public long getTimestamp() {
    return rawEvent.timestamp();
  }

  public long getSequence() {
    return sequence;
  }

  public Map<String, Object> getMetadata() {
    return extractMetadata(rawEvent);
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
    return rawEvent.errorMessage().orElse(null);
  }

  public String getTurnId() {
    return turnId;
  }

  public void setTurnId(final String turnId) {
    this.turnId = turnId;
  }

  @JsonIgnore
  public String getRawEventJson() {
    if (rawEventJson == null) {
      rawEventJson = rawEvent.toJson();
    }
    return rawEventJson;
  }

  public void setRawEventJson(final String rawEventJson) {
    this.rawEventJson = rawEventJson;
    this.rawEvent = Event.fromJson(rawEventJson);
  }

  @JsonIgnore
  public Event getRawEvent() {
    return rawEvent;
  }

  public void setRawEvent(final Event rawEvent) {
    this.rawEvent = rawEvent;
    this.rawEventJson = null; // stale cache; getRawEventJson() will recompute it on next call
  }

  public Boolean getTurnComplete() {
    return isTurnComplete();
  }

  public Boolean getPartial() {
    return isPartial();
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
        && this.sequence == that.sequence
        && Objects.equals(this.type, that.type)
        && Objects.equals(this.turnId, that.turnId)
        && Objects.equals(this.getRawEventJson(), that.getRawEventJson());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        getId(),
        rootSessionId,
        parentSessionId,
        sessionId,
        sequence,
        type,
        turnId,
        getRawEventJson());
  }

  @Override
  public String toString() {
    return "SessionEvent["
        + "id="
        + getId()
        + ", rootSessionId="
        + rootSessionId
        + ", parentSessionId="
        + parentSessionId
        + ", sessionId="
        + sessionId
        + ", runId="
        + getRunId()
        + ", author="
        + getAuthor()
        + ", content="
        + getContent()
        + ", partial="
        + isPartial()
        + ", turnComplete="
        + isTurnComplete()
        + ", finishReason="
        + getFinishReason()
        + ", timestamp="
        + getTimestamp()
        + ", type="
        + type
        + ", errorMessage="
        + getErrorMessage()
        + ", sequence="
        + sequence
        + ", metadata="
        + getMetadata()
        + ", turnId="
        + turnId
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

  public void setSequence(final long sequence) {
    this.sequence = sequence;
  }

  public static SessionEvent liveMarker(final String sessionId) {
    final String id = UUID.randomUUID().toString();
    final Event rawEvent = Event.builder().id(id).timestamp(System.currentTimeMillis()).build();
    return new SessionEvent(id, null, null, sessionId, 0L, Type.LIVE_MARKER, null, rawEvent);
  }

  public static SessionEvent terminal(final String sessionId) {
    final String id = UUID.randomUUID().toString();
    final Event rawEvent = Event.builder().id(id).timestamp(System.currentTimeMillis()).build();
    return new SessionEvent(
        id, null, null, sessionId, Long.MAX_VALUE, Type.TERMINAL, null, rawEvent);
  }

  /**
   * Creates an error event for a failed session run.
   *
   * <p>Emitted before the terminal event so the AGUI mapper can translate it to a {@code
   * RunErrorEvent}. The {@code sequence} places the error at the correct position relative to the
   * turn events that preceded the failure.
   *
   * <p>For live broadcast (where ordering is real-time), pass {@code Long.MAX_VALUE - 1}. For
   * history replay, pass the next available sequence counter so the error sorts immediately after
   * the last event from the failed turn.
   */
  public static SessionEvent error(
      final String rootSessionId,
      final String sessionId,
      final String errorMessage,
      final long sequence,
      final String turnId) {
    final String id = UUID.randomUUID().toString();
    final Event rawEvent =
        Event.builder()
            .id(id)
            .timestamp(System.currentTimeMillis())
            .errorMessage(errorMessage)
            .build();
    return new SessionEvent(
        id, rootSessionId, null, sessionId, sequence, Type.ERROR, turnId, rawEvent);
  }

  private static Map<String, Object> extractMetadata(final Event event) {
    final Map<String, Object> metadata = new HashMap<>();
    if (event.actions() != null && event.actions().stateDelta() != null) {
      for (final Map.Entry<String, Object> entry : event.actions().stateDelta().entrySet()) {
        // Strip the ADK State.TEMP_PREFIX ("temp:") so metadata keys are stored cleanly.
        String key = entry.getKey();
        key = key.startsWith(State.TEMP_PREFIX) ? key.substring(State.TEMP_PREFIX.length()) : key;
        metadata.put(key, entry.getValue());
      }
    }
    return metadata;
  }
}
