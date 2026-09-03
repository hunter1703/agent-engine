package com.agentengine.util.agents;

import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.CollectionUtils;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
   *
   * <p>This is transparent to {@code AGUITextMapper}: it already buffers every partial-text chunk
   * in memory and only emits the accumulated result once the message ends (see its {@code mode ==
   * REPLAY} branches) — string concatenation is associative, so feeding it three pre-merged chunks
   * instead of thirty small ones produces the identical final text. Only the transport cost of the
   * intermediate chunks is what this removes, not any behavior downstream.
   */
  public static List<SessionEvent> compactEventStream(final List<SessionEvent> events) {
    if (CollectionUtils.isEmpty(events)) {
      return events;
    }
    final List<SessionEvent> result = new ArrayList<>(events.size());
    final List<SessionEvent> run = new ArrayList<>();
    for (final SessionEvent event : events) {
      if (!run.isEmpty() && !sameRun(run.getLast(), event)) {
        result.add(flushRun(run));
      }
      if (isMergeableTextDelta(event)) {
        run.add(event);
      } else {
        result.add(event);
      }
    }
    if (!run.isEmpty()) {
      result.add(flushRun(run));
    }
    return result;
  }

  /**
   * Time-windowed variant of {@link #compactEventStream(List)} for a stream that's still live (a
   * currently-running session's history-replay-then-continue, or a fresh turn's live tail) — unlike
   * a completed session's history, there's no fixed end to wait for, so compacting can't run until
   * a message naturally finishes without reintroducing unbounded latency on an in-progress one.
   * Instead this groups whatever arrives within each {@code windowMillis} slice and compacts only
   * within that slice, bounding the worst case added latency to one window.
   *
   * <p>For a burst-shaped source (e.g. replaying an active session's already-committed history,
   * which — like {@link #compactEventStream(List)}'s own input — is fully available upfront and
   * flows through near-instantly) almost everything lands in the first window or two, so this
   * captures nearly the same compression as the unbounded version. For a genuinely paced live tail,
   * each window only ever holds however many deltas actually arrived in that ~100ms, preserving the
   * live typing feel — {@code AGUITextMapper}'s LIVE mode emits whatever chunk it's given as its
   * own {@code TextMessageChunkEvent} with no notion of "the whole message" to wait for, so a run
   * split at a window boundary just becomes two chunks instead of one; the client concatenates
   * deltas as they arrive regardless of how they were chunked.
   */
  public static Flowable<SessionEvent> compactEventStream(
      final Flowable<SessionEvent> events, final long windowMillis) {
    return events
        .buffer(windowMillis, TimeUnit.MILLISECONDS)
        .filter(batch -> !batch.isEmpty())
        .concatMapIterable(SessionEventUtils::compactEventStream);
  }

  private static SessionEvent flushRun(final List<SessionEvent> run) {
    final SessionEvent flushed = run.size() == 1 ? run.getFirst() : merge(run);
    run.clear();
    return flushed;
  }

  private static boolean isMergeableTextDelta(final SessionEvent event) {
    if (!Boolean.TRUE.equals(event.isPartial())) {
      return false;
    }
    final List<Part> parts = parts(event);
    if (parts.isEmpty()) {
      return false;
    }
    final boolean thought = parts.getFirst().thought().orElse(false);
    for (final Part part : parts) {
      if (part.text().isEmpty()
          || part.functionCall().isPresent()
          || part.functionResponse().isPresent()
          || part.inlineData().isPresent()
          || part.fileData().isPresent()
          || part.executableCode().isPresent()
          || part.codeExecutionResult().isPresent()
          || !Objects.equals(part.thought().orElse(false), thought)) {
        return false;
      }
    }
    return true;
  }

  private static boolean sameRun(final SessionEvent previous, final SessionEvent candidate) {
    if (!isMergeableTextDelta(previous) || !isMergeableTextDelta(candidate)) {
      // Only matters when candidate is itself mergeable; isMergeableTextDelta is re-checked by
      // the caller either way, this just avoids NPEs reading fields off a non-matching previous.
      return false;
    }
    return Objects.equals(previous.getSessionId(), candidate.getSessionId())
        && Objects.equals(previous.getRunId(), candidate.getRunId())
        && Objects.equals(previous.getAuthor(), candidate.getAuthor())
        && Objects.equals(previous.getTurnId(), candidate.getTurnId())
        && Objects.equals(thoughtFlag(previous), thoughtFlag(candidate));
  }

  private static List<Part> parts(final SessionEvent event) {
    final Content content = event.getContent();
    return content == null ? List.of() : content.parts().orElse(List.of());
  }

  // Only called on an event already confirmed mergeable, which guarantees every part shares one
  // thought flag — so the first part's is the event's.
  private static boolean thoughtFlag(final SessionEvent event) {
    return parts(event).getFirst().thought().orElse(false);
  }

  private static SessionEvent merge(final List<SessionEvent> run) {
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
}
