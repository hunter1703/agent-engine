package com.agentengine.agent.infra.utils;

import com.agentengine.agent.api.model.MessagePart;
import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.SessionEventUtils;
import com.agentengine.util.agents.beans.ResumeRequest;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.MarkdownUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.Violation;
import com.agentengine.util.common.beans.FileDetails;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.events.ToolConfirmation;
import com.google.adk.flows.llmflows.Functions;
import com.google.adk.sessions.State;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Utilities for working with runtime {@link Event} objects. */
public final class EventUtils {

  public static final String VIOLATION_KEY = State.TEMP_PREFIX + SessionEventUtils.VIOLATION;

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
    ConcurrentMap<String, Object> delta = actions.stateDelta();
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
      final UserMessage userMessage, final String invocationId, final long timestamp) {
    final Event event =
        _buildUserEvent(
            invocationId,
            com.agentengine.agent.infra.utils.ContentUtils.buildUserContent(userMessage),
            timestamp);
    final List<FileDetails> attachments =
        userMessage.parts().stream()
            .filter(part -> part instanceof MessagePart.FilePart)
            .map(part -> ((MessagePart.FilePart) part).fileDetails())
            .toList();
    if (!attachments.isEmpty()) {
      addMetadata(event, SessionEventUtils.ATTACHMENTS, attachments);
    }
    return event;
  }

  public static Event buildResumeEvent(
      final Collection<ResumeRequest> resumeRequests,
      final String invocationId,
      final long timestamp) {
    final Content resumeContent = ContentUtils.buildResumeContent(resumeRequests);
    return _buildUserEvent(invocationId, resumeContent, timestamp);
  }

  private static Event _buildUserEvent(
      final String invocationId, final Content content, final long timestamp) {
    return Event.builder()
        .id(Event.generateEventId())
        .invocationId(invocationId)
        .author(Constants.AUTHOR_USER)
        .content(content)
        .timestamp(timestamp)
        .build();
  }

  public static Event buildCorrectiveEvent(
      final InvocationContext context, final Violation violation) {
    final String prompt =
        """
                Violations were detected in your previous response. Please resolve or correct them.

                > Some of your replies may have been stripped from the history because they caused \
                violations that must not be persisted. Use the violation details and corrective steps \
                below as a guide.

                ## Violations

                """
            + MarkdownUtils.fromObject(violation);

    final Content correctiveContent =
        Content.builder().role(Constants.AUTHOR_USER).parts(List.of(Part.fromText(prompt))).build();

    final ConcurrentHashMap<String, Object> stateDelta = new ConcurrentHashMap<>();
    stateDelta.put(VIOLATION_KEY, violation);
    final EventActions actions = EventActions.builder().stateDelta(stateDelta).build();
    return Event.builder()
        .id(Event.generateEventId())
        .invocationId(context.invocationId())
        .author(correctiveContent.role().orElseThrow())
        .branch(context.branch())
        .actions(actions)
        .content(correctiveContent)
        .build();
  }
}
