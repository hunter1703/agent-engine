package com.agentengine.agent.infra.utils;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.util.agents.AgentFileDetails;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.SessionEventUtils;
import com.agentengine.util.agents.beans.ResumeRequest;
import com.agentengine.util.agents.beans.Signal;
import com.agentengine.util.common.Violation;
import com.agentengine.util.common.codec.JsonUtils;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
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

  /**
   * The message that started the latest invocation started by one: going back invocation by
   * invocation, the first event of an invocation that is a user's message rather than a tool result
   * (e.g. a resume). Runtime instructions are delivered after an invocation's first event, so they
   * are never it. Compaction events carry an invocation id of their own and are not part of any.
   */
  public static Content findLatestUserMessage(final List<Event> events) {
    final List<Event> invocationEvents =
        CollectionUtils.nullSafeList(events).stream().filter(event -> !isCompactionEvent(event)).toList();
    int index = invocationEvents.size() - 1;
    while (index >= 0) {
      final Event event = invocationEvents.get(index);
      final boolean startsInvocation =
          index == 0
              || !Objects.equals(
                  invocationEvents.get(index - 1).invocationId(), event.invocationId());
      if (startsInvocation
          && Constants.AUTHOR_USER.equals(event.author())
          && event.functionResponses().isEmpty()
          && event.content().isPresent()) {
        return event.content().get();
      }
      index--;
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
      final String author) {
    final Event event =
        _buildUserEvent(
            invocationId,
            ContentUtils.buildUserContent(ContentUtils.textParts(userMessage.parts()), null),
            timestamp,
            author);
    final List<AgentFileDetails> attachments = userMessage.attachments();
    if (CollectionUtils.isNotEmpty(attachments)) {
      addMetadata(event, SessionEventUtils.ATTACHMENTS, attachments);
    }
    return event;
  }

  public static Event buildResumeEvent(
      final Collection<ResumeRequest> resumeRequests,
      final String invocationId,
      final String author) {
    final Content resumeContent = ContentUtils.buildResumeContent(resumeRequests);
    final long resumeTimestamp =
        resumeRequests.stream()
            .mapToLong(ResumeRequest::getTimestamp)
            .max()
            .orElse(System.currentTimeMillis());
    return _buildUserEvent(invocationId, resumeContent, resumeTimestamp, author);
  }

  private static Event _buildUserEvent(
      final String invocationId, final Content content, final long timestamp, final String author) {
    return Event.builder()
        .id(Event.generateEventId())
        .invocationId(invocationId)
        .author(author)
        .content(content)
        .timestamp(timestamp)
        .build();
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

                > Some of your replies may have been stripped from the history because they caused \
                violations that must not be persisted.

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

  public static Event enrichWithAttachments(final Event event) {
    final Map<String, Object> stateDelta =
        event.actions() == null ? null : event.actions().stateDelta();
    final List<AgentFileDetails> attachments =
        CollectionUtils.getListFromMap(
            stateDelta, State.TEMP_PREFIX + SessionEventUtils.ATTACHMENTS);
    if (attachments.isEmpty() || event.content().isEmpty()) {
      return event;
    }
    return event.toBuilder()
        .content(com.agentengine.util.agents.ContentUtils.addAttachmentsToContent(event.content().get(), attachments))
        .build();
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
      final Event event, final long startTimestamp, final long endTimestamp, final Content content) {
    final EventActions actions = event.actions() == null ? new EventActions() : event.actions();
    final EventCompaction compaction =
        EventCompaction.builder()
            .startTimestamp(startTimestamp)
            .endTimestamp(endTimestamp)
            .compactedContent(content)
            .build();
    return event.toBuilder().actions(actions.toBuilder().compaction(compaction).build()).build();
  }
}
