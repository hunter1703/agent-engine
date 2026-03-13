package com.agentengine.engine.hitl;

import static com.google.adk.flows.llmflows.Functions.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.Functions;
import com.google.genai.types.FunctionCall;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record SessionPauseView(SessionPauseKind kind, String prompt, List<String> options, String confirmationId) {
  private static final String TOOL_NAME_HUMAN_IN_THE_LOOP = "human_in_the_loop";
  private static final String TOOL_NAME_REQUEST_HUMAN_INPUT = "request_human_input";
  private static final String ARG_PROMPT = "prompt";
  private static final String ARG_KIND = "kind";
  private static final String ARG_OPTIONS = "options";
  private static final List<String> DECISION_OPTIONS = List.of(ConfirmationDecision.ALLOW.name(), ConfirmationDecision.DISALLOW.name());
  private static final String ARG_ORIGINAL_FUNCTION_CALL = "originalFunctionCall";
  private static final String ARG_PAYLOAD = "payload";
  private static final String ARG_TOOL_CONFIRMATION = "toolConfirmation";
  private static final String ARG_HINT = "hint";
  private static final String ARG_NAME = "name";
  private static final String ARG_ARGS = "args";
  private static final SessionPauseView NOT_PAUSED = new SessionPauseView(SessionPauseKind.UNKNOWN, null, List.of(), null);

  public SessionPauseView {
    kind = kind == null ? SessionPauseKind.UNKNOWN : kind;
    options = options == null ? List.of() : List.copyOf(options);
  }

  public static SessionPauseView from(final List<Event> events) {
    if (CollectionUtils.isEmpty(events)) {
      return NOT_PAUSED;
    }
    final Set<String> respondedConfirmationIds = findRespondedConfirmationIds(events);
    for (int eventIndex = events.size() - 1; eventIndex >= 0; eventIndex--) {
      final Event event = events.get(eventIndex);
      if (event == null) {
        continue;
      }
      final List<FunctionCall> confirmationCalls = Functions.getAskUserConfirmationFunctionCalls(event);
      for (int callIndex = confirmationCalls.size() - 1; callIndex >= 0; callIndex--) {
        final FunctionCall confirmationCall = confirmationCalls.get(callIndex);
        final String confirmationId = confirmationCall.id().orElse(null);
        if (StringUtils.isBlank(confirmationId) || respondedConfirmationIds.contains(confirmationId)) {
          continue;
        }
        return buildPauseView(confirmationCall, confirmationId);
      }
    }
    return NOT_PAUSED;
  }

  public boolean isPaused() {
    return kind != SessionPauseKind.UNKNOWN;
  }

  public boolean hasConfirmationId() {
    return StringUtils.isNotBlank(confirmationId);
  }

  private static Set<String> findRespondedConfirmationIds(final List<Event> events) {
    final Set<String> respondedConfirmationIds = new HashSet<>();
    for (final Event event : events) {
      if (event == null) {
        continue;
      }
      event.functionResponses().stream().filter(response -> REQUEST_CONFIRMATION_FUNCTION_CALL_NAME.equals(response.name().orElse(null)))
          .flatMap(response -> response.id().stream()).forEach(respondedConfirmationIds::add);
    }
    return respondedConfirmationIds;
  }

  private static SessionPauseView buildPauseView(final FunctionCall confirmationCall, final String confirmationId) {
    final Map<String, Object> confirmationArgs = CollectionUtils.nullSafeMap(confirmationCall.args().orElse(Map.of()));
    final Map<String, Object> toolConfirmation = CollectionUtils.getMapFromMap(confirmationArgs, ARG_TOOL_CONFIRMATION);
    final Map<String, Object> originalFunctionCall = CollectionUtils.getMapFromMap(confirmationArgs, ARG_ORIGINAL_FUNCTION_CALL);
    final Map<String, Object> originalArgs = CollectionUtils.getMapFromMap(originalFunctionCall, ARG_ARGS);
    final Map<String, Object> confirmationPayload = CollectionUtils.getMapFromMap(toolConfirmation, ARG_PAYLOAD);
    final String toolName = CollectionUtils.getStringValueFromMap(originalFunctionCall, ARG_NAME);
    final SessionPauseKind kind = getKind(toolName, originalArgs, confirmationPayload);
    return new SessionPauseView(kind, buildPrompt(toolConfirmation, originalArgs), buildOptions(kind, originalArgs, confirmationPayload),
        confirmationId);
  }

  private static SessionPauseKind getKind(final String toolName, final Map<String, Object> originalArgs,
      final Map<String, Object> confirmationPayload) {
    if (!isHumanInputTool(toolName)) {
      return SessionPauseKind.DECISION;
    }
    final SessionPauseKind fromArgs = SessionPauseKind.valueOfOrDefault(CollectionUtils.getStringValueFromMap(originalArgs, ARG_KIND));
    if (fromArgs != SessionPauseKind.UNKNOWN) {
      return fromArgs;
    }
    return SessionPauseKind.valueOfOrDefault(CollectionUtils.getStringValueFromMap(confirmationPayload, ARG_KIND));
  }

  private static String buildPrompt(final Map<String, Object> toolConfirmation, final Map<String, Object> originalArgs) {
    final String hint = CollectionUtils.getStringValueFromMap(toolConfirmation, ARG_HINT);
    return StringUtils.isNotBlank(hint) ? hint : CollectionUtils.getStringValueFromMap(originalArgs, ARG_PROMPT);
  }

  private static List<String> buildOptions(final SessionPauseKind kind, final Map<String, Object> originalArgs,
      final Map<String, Object> confirmationPayload) {
    final List<String> explicitOptions = CollectionUtils.getListFromMap(originalArgs, ARG_OPTIONS);
    if (!explicitOptions.isEmpty()) {
      return explicitOptions;
    }
    final List<String> payloadOptions = CollectionUtils.getListFromMap(confirmationPayload, ARG_OPTIONS);
    if (!payloadOptions.isEmpty()) {
      return payloadOptions;
    }
    return kind == SessionPauseKind.DECISION ? DECISION_OPTIONS : List.of();
  }

  private static boolean isHumanInputTool(final String toolName) {
    return TOOL_NAME_HUMAN_IN_THE_LOOP.equals(toolName) || TOOL_NAME_REQUEST_HUMAN_INPUT.equals(toolName);
  }
}
