package com.agentengine.util.agents;

import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.FlowableUtils;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class SessionEventUtils {

  public static final String VIOLATION = "violation";
  public static final String INTERNAL = "internal";
  public static final String SESSION_ID = "sessionId";
  public static final String ATTACHMENTS = "attachments";

  private SessionEventUtils() {}

  public static boolean isCorrectionEvent(final SessionEvent event) {
    final Map<String, Object> metadata =
        CollectionUtils.nullSafeMap(event == null ? null : event.getMetadata());
    return Boolean.TRUE.equals(CollectionUtils.getBooleanValueFromMap(metadata, VIOLATION));
  }

  public static boolean isInternal(final SessionEvent event) {
    final Map<String, Object> metadata =
        CollectionUtils.nullSafeMap(event == null ? null : event.getMetadata());
    return Boolean.TRUE.equals(CollectionUtils.getBooleanValueFromMap(metadata, INTERNAL));
  }

  public static List<SessionEvent> toSessionEvents(
      final String rootSessionId,
      final String parentSessionId,
      final String sessionId,
      final String turnId,
      final List<Event> events,
      final long startSequence) {
    if (CollectionUtils.isEmpty(events)) {
      return List.of();
    }
    final List<SessionEvent> sessionEvents = new ArrayList<>(events.size());
    for (int index = 0; index < events.size(); index++) {
      sessionEvents.add(
          toSessionEvent(
              rootSessionId,
              parentSessionId,
              sessionId,
              turnId,
              events.get(index),
              startSequence + index));
    }
    return sessionEvents;
  }

  public static SessionEvent toSessionEvent(
      final String rootSessionId,
      final String parentSessionId,
      final String sessionId,
      final String turnId,
      final Event event,
      final long sequence) {
    return new SessionEvent(
        event.id(),
        rootSessionId,
        parentSessionId,
        sessionId,
        sequence,
        SessionEvent.Type.NORMAL,
        turnId,
        event);
  }

  /**
   * Merges consecutive streamed-text {@code SessionEvent}s (same session/run/author, both {@code
   * isPartial()}, same {@code thought} flag across every part) into one, concatenating their text.
   * An event only qualifies when every one of its parts is plain text — no function
   * calls/responses, attachments, or code — and all share one {@code thought} flag; events that
   * aren't part of such a run pass through unchanged.
   */
  public static List<SessionEvent> compactEventStream(final List<SessionEvent> events) {
    if (CollectionUtils.isEmpty(events)) {
      return events;
    }
    final List<SessionEvent> result = new ArrayList<>(events.size());
    final List<SessionEvent> run = new ArrayList<>();
    for (final SessionEvent event : events) {
      if (sameRun(run, event) && isTextDelta(event)) {
        run.add(event);
        continue;
      }
      final SessionEvent flushed = merge(run);
      if (flushed != null) {
        result.add(flushed);
      }
      run.clear();
      if (isTextDelta(event)) {
        // another run started
        run.add(event);
      } else {
        result.add(event);
      }
    }
    final SessionEvent flushed = merge(run);
    if (flushed != null) {
      result.add(flushed);
    }
    return result;
  }

  private static boolean sameRun(final List<SessionEvent> run, final SessionEvent candidate) {
    return !run.isEmpty() && sameRun(run.getLast(), candidate);
  }

  private static boolean sameRun(final SessionEvent previous, final SessionEvent candidate) {
    final List<Part> candidateParts = parts(candidate);
    if (candidateParts.isEmpty()) {
      return false;
    }
    return sameTurn(previous, candidate)
        && Objects.equals(thoughtFlag(previous), candidateParts.getFirst().thought().orElse(false));
  }

  private static boolean sameTurn(final SessionEvent one, final SessionEvent two) {
    return Objects.equals(one.getSessionId(), two.getSessionId())
        && Objects.equals(one.getRunId(), two.getRunId())
        && Objects.equals(one.getAuthor(), two.getAuthor())
        && Objects.equals(one.getTurnId(), two.getTurnId());
  }

  public static Flowable<SessionEvent> compactEventStream(
      final Flowable<SessionEvent> events, final long windowMillis) {
    return events
        .buffer(windowMillis, TimeUnit.MILLISECONDS, FlowableUtils.streamingScheduler())
        .filter(batch -> !batch.isEmpty())
        .concatMapIterable(SessionEventUtils::compactEventStream);
  }

  private static SessionEvent merge(final List<SessionEvent> run) {
    if (run.isEmpty()) {
      return null;
    }
    if (run.size() == 1) {
      return run.getFirst();
    }

    final SessionEvent last = run.getLast();
    final StringBuilder mergedText = new StringBuilder();
    for (final SessionEvent event : run) {
      for (final Part part : parts(event)) {
        mergedText.append(part.text().orElse(""));
      }
    }
    final Part mergedPart = parts(last).getFirst().toBuilder().text(mergedText.toString()).build();
    final Content mergedContent = last.getContent().toBuilder().parts(List.of(mergedPart)).build();
    last.setRawEvent(last.getRawEvent().toBuilder().content(mergedContent).build());
    return last;
  }

  private static boolean isTextDelta(final SessionEvent event) {
    if (!Boolean.TRUE.equals(event.isPartial())) {
      return false;
    }
    final List<Part> parts = parts(event);

    // A partial delta is always exactly one part — LangChain4jModel's onPartialResponse and
    // onPartialThinking each emit a single text-or-thought part, never combined, never more than
    // one — so there's no multipart case to reconcile here.
    final Part part = parts.getFirst();
    return part.text().isPresent()
        && part.functionCall().isEmpty()
        && part.functionResponse().isEmpty()
        && part.inlineData().isEmpty()
        && part.fileData().isEmpty()
        && part.executableCode().isEmpty()
        && part.codeExecutionResult().isEmpty();
  }

  private static List<Part> parts(final SessionEvent event) {
    final Content content = event.getContent();
    return content == null ? List.of() : content.parts().orElse(List.of());
  }

  // Only called on an event already confirmed mergeable, which guarantees exactly one part.
  private static boolean thoughtFlag(final SessionEvent event) {
    return parts(event).getFirst().thought().orElse(false);
  }
}
