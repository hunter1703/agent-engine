package com.agentengine.agent.infra.utils;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.util.agents.AgentFileDetails;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.ContentUtils;
import com.agentengine.util.agents.SessionEventUtils;
import com.agentengine.util.agents.beans.ResumeRequest;
import com.agentengine.util.agents.beans.Signal;
import com.agentengine.util.common.Violation;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.codec.JsonUtils;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.common.utils.Utils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.events.EventCompaction;
import com.google.adk.events.ToolConfirmation;
import com.google.adk.flows.llmflows.Functions;
import com.google.adk.sessions.State;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Utilities for working with runtime {@link Event} objects. */
public final class EventUtils {

  public static final String VIOLATION_KEY = State.TEMP_PREFIX + SessionEventUtils.VIOLATION;

  /** The state delta key holding when a message was sent, as an ISO-8601 zoned time. */
  public static final String SENT_AT_KEY = State.TEMP_PREFIX + "sent_at";

  /** The state delta key holding attachments of the message sent. */
  public static final String ATTACHMENTS_KEY = State.TEMP_PREFIX + SessionEventUtils.ATTACHMENTS;

  /**
   * The session state key holding the id of the time zone of the latest message sent, so a run's
   * zone is read from the state and not looked for in the events. Not a {@code temp:} key, so it
   * stays in the session state.
   */
  public static final String TIME_ZONE_KEY = "time_zone";

  /**
   * Marks the event carrying this stateDelta key as the one a {@link Signal} was raised against —
   * written by {@code RunState.addSignal(CallbackContext, Signal)} onto whatever event the
   * triggering callback's context is tied to. Paired with {@link #SIGNAL_DELIVERED_KEY} so {@code
   * RunState.buildFrom} can tell, purely from persisted history, which raised signals never made it
   * into a request and need to be re-queued.
   */
  public static final String SIGNAL_RAISED_KEY = State.TEMP_PREFIX + "raised_signals";

  /**
   * Marks the event carrying this stateDelta key as the actual delivery of one or more signals —
   * written by {@code RunState.clearSignals(List)} onto the event(s) {@code SignalProcessor} just
   * built to carry them. See {@link #SIGNAL_RAISED_KEY}.
   */
  public static final String SIGNAL_DELIVERED_KEY = State.TEMP_PREFIX + "delivered_signal_ids";

  private EventUtils() {}

  // not adding condition on event.finishReason() as ADK agentic loop ignores this
  // for termination
  // detection
  public static boolean isTerminal(final Event event) {
    if (event == null) {
      return false;
    }
    final boolean endInvocation =
        event.actions() != null && event.actions().endInvocation().orElse(false);
    return event.finalResponse() || endInvocation;
  }

  /**
   * Marks an event as internal so it is excluded from end-user-facing output (e.g. AG-UI events)
   * while remaining fully visible to the LLM as session history.
   */
  public static void markAsInternal(final Event event) {
    addMetadata(event, SessionEventUtils.INTERNAL, true);
  }

  public static void addMetadata(final Event event, final String key, final Object value) {
    if (event == null) {
      return;
    }
    EventActions actions = event.actions();
    if (actions == null) {
      actions = new EventActions();
    }
    Map<String, Object> delta = actions.stateDelta();
    if (delta == null) {
      delta = new ConcurrentHashMap<>();
    }
    delta.put(State.TEMP_PREFIX + key, value);
    event.setActions(actions.toBuilder().stateDelta(delta).build());
  }

  /**
   * Scans events in reverse chronological order and returns the most recent value for {@code key}.
   * Returns {@code null} if the key has never been set or was explicitly removed via {@link
   * State#REMOVED}.
   */
  public static Object latestDeltaValue(final List<Event> events, final String key) {
    if (CollectionUtils.isEmpty(events) || StringUtils.isBlank(key)) {
      return null;
    }
    for (final Event event : events.reversed()) {
      if (event == null || event.actions() == null) {
        continue;
      }
      final Map<String, Object> delta = event.actions().stateDelta();
      if (CollectionUtils.isEmpty(delta) || !delta.containsKey(key)) {
        continue;
      }
      final Object value = delta.get(key);
      return State.REMOVED.equals(value) ? null : value;
    }
    return null;
  }

  public static String recentUser(final List<Event> events, final int max) {
    if (events == null || events.isEmpty()) {
      return "";
    }
    final List<String> intents = new ArrayList<>();
    for (int i = events.size() - 1; i >= 0 && intents.size() < max; i--) {
      final Content content = events.get(i).content().orElse(null);
      if (content == null || !Constants.AUTHOR_USER.equals(content.role().orElse(""))) {
        continue;
      }
      final String text = content.text();
      if (StringUtils.isNotBlank(text)) {
        intents.add(text);
      }
    }
    return String.join("\n", intents.reversed());
  }

  public static Event buildResumeAnswerEvent(
      final String interruptId, final Boolean accepted, final Map<String, Object> answer) {
    final ToolConfirmation toolConfirmation = ResponseUtils.buildToolConfirmation(accepted, answer);
    final FunctionResponse functionResponse =
        FunctionResponse.builder()
            .id(interruptId)
            .name(Functions.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME)
            .response(JsonUtils.toMap(toolConfirmation))
            .build();
    return Event.builder()
        .id(interruptId)
        .author(Constants.AUTHOR_USER)
        .content(
            Content.builder()
                .role(Constants.AUTHOR_USER)
                .parts(List.of(Part.builder().functionResponse(functionResponse).build()))
                .build())
        .build();
  }

  public static Event buildUserEvent(
      final UserMessage userMessage,
      final String invocationId,
      final long timestamp,
      final String author,
      final Map<String, Object> stateDelta) {
    final Content textContent =
        RequestContentUtils.buildUserContent(RequestContentUtils.textParts(userMessage.parts()));
    return _buildUserEvent(invocationId, textContent, timestamp, author, stateDelta);
  }

  /** What is kept with a message's event, and handed to the runner, for when it was sent. */
  public static Map<String, Object> sentAtDelta(final ZonedDateTime sentAt) {
    return Map.of(SENT_AT_KEY, sentAt.toString(), TIME_ZONE_KEY, sentAt.getZone().getId());
  }

  /**
   * What is kept with a message's event, and handed to the runner, for when it was sent and its
   * attachments.
   */
  public static Map<String, Object> userStateDelta(
      final ZonedDateTime sentAt, final List<AgentFileDetails> attachments) {
    final Map<String, Object> delta = CollectionUtils.nullSafeMutableMap(sentAtDelta(sentAt));
    if (CollectionUtils.isNotEmpty(attachments)) {
      delta.put(ATTACHMENTS_KEY, attachments);
    }
    return delta;
  }

  public static Event buildResumeEvent(
      final Collection<ResumeRequest> resumeRequests,
      final String invocationId,
      final String author) {
    final Content resumeContent = RequestContentUtils.buildResumeContent(resumeRequests);
    final long resumeTimestamp =
        resumeRequests.stream()
            .mapToLong(ResumeRequest::getTimestamp)
            .max()
            .orElse(System.currentTimeMillis());
    return _buildUserEvent(invocationId, resumeContent, resumeTimestamp, author, null);
  }

  private static Event _buildUserEvent(
      final String invocationId,
      final Content content,
      final long timestamp,
      final String author,
      final Map<String, Object> stateDelta) {
    final Event.Builder builder =
        Event.builder()
            .id(Event.generateEventId())
            .invocationId(invocationId)
            .author(author)
            .content(content)
            .timestamp(timestamp);
    if (CollectionUtils.isNotEmpty(stateDelta)) {
      builder.actions(
          EventActions.builder().stateDelta(new ConcurrentHashMap<>(stateDelta)).build());
    }
    return builder.build();
  }

  public static Event buildCorrectiveEvent(
      final InvocationContext context, final List<Violation> violations) {
    final StringBuilder body = new StringBuilder();
    for (final Violation violation : violations) {
      body.append("\n- ").append(violation.message());
      for (final Map.Entry<String, Object> detail :
          CollectionUtils.nullSafeMap(violation.details()).entrySet()) {
        body.append("\n  - ").append(detail.getKey()).append(": ").append(detail.getValue());
      }
    }
    final String prompt =
        """
                Violations were detected in your previous response. Please resolve or correct them.

                > Your previous reply violated protocol constraints. Your earlier tool calls \
                and results are already recorded in the conversation — do not repeat completed work. \
                Follow the instructions below to proceed.

                ## Violations
                """
            + body;

    final Content correctiveContent =
        Content.builder().role(Constants.AUTHOR_USER).parts(List.of(Part.fromText(prompt))).build();

    final ConcurrentHashMap<String, Object> stateDelta = new ConcurrentHashMap<>();
    stateDelta.put(VIOLATION_KEY, violations);
    final EventActions actions = EventActions.builder().stateDelta(stateDelta).build();
    return Event.builder()
        .id(Event.generateEventId())
        .invocationId(context.invocationId())
        .author(correctiveContent.role().orElseThrow())
        .branch(context.branch().orElse(null))
        .actions(actions)
        .content(correctiveContent)
        .build();
  }

  public static Event buildUserTextEvent(final InvocationContext context, final String message) {
    final Content updateContent =
        Content.builder()
            .role(Constants.AUTHOR_USER)
            .parts(List.of(Part.fromText(message)))
            .build();
    return Event.builder()
        .id(Event.generateEventId())
        .invocationId(context.invocationId())
        .author(updateContent.role().orElseThrow())
        .branch(context.branch().orElse(null))
        .content(updateContent)
        .build();
  }

  public static List<AgentFileDetails> getAttachments(final Event event) {
    if (event == null || event.actions() == null) {
      return List.of();
    }
    final Map<String, Object> stateDelta = event.actions().stateDelta();
    if (CollectionUtils.isEmpty(stateDelta)) {
      return List.of();
    }
    final List<?> raw = CollectionUtils.getValueFromMap(stateDelta, ATTACHMENTS_KEY);
    return CollectionUtils.nullSafeList(raw).stream()
        .filter(Objects::nonNull)
        .map(
            item -> {
              if (item instanceof AgentFileDetails agentFileDetails) {
                return agentFileDetails;
              }
              if (item instanceof FileDetails fileDetails) {
                return new AgentFileDetails(fileDetails);
              }
              return Utils.convertValue(item, AgentFileDetails.class);
            })
        .filter(Objects::nonNull)
        .toList();
  }

  /**
   * {@code compactionEvent} with its range ended before the earliest call it covers whose result
   * comes after the range, so a call and its result are never split across it; null when no events
   * remain in the range.
   */
  public static Event keepCallsWithResults(final Event compactionEvent, final List<Event> events) {
    final EventCompaction compaction = compactionEvent.actions().compaction().orElseThrow();
    final Map<String, Long> callIdVsTimestamp = new HashMap<>();
    long earliestSplitCall = Long.MAX_VALUE;
    for (final Event event : events) {
      if (isCompactionEvent(event) || event.timestamp() < compaction.startTimestamp()) {
        continue;
      }
      if (event.timestamp() <= compaction.endTimestamp()) {
        event
            .functionCalls()
            .forEach(
                call -> call.id().ifPresent(id -> callIdVsTimestamp.put(id, event.timestamp())));
        continue;
      }
      for (final FunctionResponse response : event.functionResponses()) {
        final Long callTimestamp = response.id().map(callIdVsTimestamp::get).orElse(null);
        if (callTimestamp != null) {
          earliestSplitCall = Math.min(earliestSplitCall, callTimestamp);
        }
      }
    }
    if (earliestSplitCall == Long.MAX_VALUE) {
      return compactionEvent;
    }
    final long endTimestamp = earliestSplitCall - 1;
    if (endTimestamp < compaction.startTimestamp()) {
      return null;
    }
    return withCompaction(
        compactionEvent, compaction.startTimestamp(), endTimestamp, compaction.compactedContent());
  }

  public static Event withCompactedContent(final Event compactedEvent, final Content content) {
    final EventCompaction compaction = compactedEvent.actions().compaction().orElseThrow();
    return withCompaction(
        compactedEvent, compaction.startTimestamp(), compaction.endTimestamp(), content);
  }

  public static boolean isCompactionEvent(final Event event) {
    return event.actions() != null && event.actions().compaction().isPresent();
  }

  private static Event withCompaction(
      final Event event,
      final long startTimestamp,
      final long endTimestamp,
      final Content content) {
    final EventActions actions = event.actions() == null ? new EventActions() : event.actions();
    final EventCompaction compaction =
        EventCompaction.builder()
            .startTimestamp(startTimestamp)
            .endTimestamp(endTimestamp)
            .compactedContent(content)
            .build();
    return event.toBuilder().actions(actions.toBuilder().compaction(compaction).build()).build();
  }

  public static boolean isModelEvent(final Event event) {
    return event != null && event.content().map(ContentUtils::isModelContent).orElse(false);
  }
}
