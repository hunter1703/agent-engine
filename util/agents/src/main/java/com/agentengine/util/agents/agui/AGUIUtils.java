package com.agentengine.util.agents.agui;

import com.agentengine.util.agents.beans.InterruptKind;
import com.agentengine.util.common.Violation;
import com.agentengine.util.common.beans.FileDetails;
import com.agui.community.core.event.CustomEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory for custom AG-UI events that don't have a standard event type in the AG-UI spec.
 *
 * <p>All methods return {@link CustomEvent} instances (which are valid {@code BaseEvent}
 * subclasses) constructed with the event-specific name and a plain {@code Map} payload.
 */
public final class AGUIUtils {

  private AGUIUtils() {}

  /**
   * Emitted when a message part contains an attachment (e.g. an image produced by the agent).
   * Emitted after any text chunks and before the {@code TextMessageEndEvent} of the parent message,
   * so consumers can associate the attachment with the correct message.
   */
  public static CustomEvent buildAttachmentEvent(
      final String parentMessageId, final FileDetails fileDetails, final long timestamp) {
    final Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("parentMessageId", parentMessageId);
    fields.put("fileDetails", fileDetailsJson(fileDetails));
    return new CustomEvent("attachment", fields, timestamp, null);
  }

  /**
   * Emitted when a session pauses to raise an interrupt requesting human input. {@code interruptId}
   * is the ID the client must echo back via the resume endpoint.
   */
  public static CustomEvent buildInterruptRequestedEvent(
      final String interruptId,
      final String prompt,
      final String originalToolCallId,
      final List<String> options,
      final InterruptKind kind,
      final long timestamp) {
    final Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("interruptId", interruptId);
    fields.put("prompt", prompt);
    fields.put("originalToolCallId", originalToolCallId);
    fields.put("options", options);
    fields.put("kind", kind == null ? null : kind.name());
    return new CustomEvent("interrupt_requested", fields, timestamp, null);
  }

  /** Emitted when a user resumes a paused session by answering an interrupt. */
  public static CustomEvent buildResumedEvent(
      final String interruptId, final boolean accepted, final String answer, final long timestamp) {
    final Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("interruptId", interruptId);
    fields.put("accepted", accepted);
    fields.put("answer", answer);
    return new CustomEvent("resumed", fields, timestamp, null);
  }

  /** Emitted when a guardrail violation is detected and corrected. */
  public static CustomEvent buildCorrectionEvent(final Violation violation, final long timestamp) {
    return new CustomEvent("correction", Map.of("message", violation.message()), timestamp, null);
  }

  private static Map<String, Object> fileDetailsJson(final FileDetails fileDetails) {
    if (fileDetails == null) {
      return Map.of();
    }
    final Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("name", fileDetails.name());
    fields.put("source", fileDetails.source());
    fields.put("type", fileDetails.type() != null ? fileDetails.type().name() : null);
    fields.put("mimeType", fileDetails.mimeType());
    fields.put("size", fileDetails.size());
    return fields;
  }
}
