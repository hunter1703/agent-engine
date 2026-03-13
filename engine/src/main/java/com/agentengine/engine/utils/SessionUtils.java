package com.agentengine.engine.utils;

import com.agentengine.engine.hitl.SessionPause;
import com.agentengine.engine.hitl.SessionPauseKind;
import com.agentengine.engine.tools.HumanInTheLoopTool;
import com.agentengine.engine.tools.ToolUtils;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.Utils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.events.ToolConfirmation;
import com.google.adk.flows.llmflows.Functions;
import com.google.genai.types.FunctionCall;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class SessionUtils {
  private static final String ARG_ORIGINAL_FUNCTION_CALL = "originalFunctionCall";
  private static final SessionPause NOT_PAUSED = new SessionPause(SessionPauseKind.UNKNOWN, null);

  private SessionUtils() {
  }

  public static ConcurrentMap<String, Object> buildInitialState() {
    return new ConcurrentHashMap<>();
  }

  public static ConcurrentMap<String, Object> state(final InvocationContext context) {
    if (context == null || context.session() == null || context.session().state() == null) {
      return null;
    }
    return context.session().state();
  }

  public static SessionPause pauseView(final List<Event> events) {
    if (CollectionUtils.isEmpty(events)) {
      return NOT_PAUSED;
    }
    final Set<String> respondedIds = ToolUtils.findRespondedConfirmationIds(events);
    for (final Event event : events.reversed()) {
      final List<FunctionCall> confirmationCalls = Functions.getAskUserConfirmationFunctionCalls(event);
      for (final FunctionCall call : confirmationCalls.reversed()) {
        final String confirmationId = call.id().orElse(null);
        if (respondedIds.contains(confirmationId)) {
          continue;
        }
        return buildPauseView(call, confirmationId);
      }
    }
    return NOT_PAUSED;
  }

  private static SessionPause buildPauseView(final FunctionCall confirmationCall, final String confirmationId) {
    final Map<String, Object> args = CollectionUtils.nullSafeMap(confirmationCall.args().orElse(Map.of()));
    final FunctionCall originalCall = Utils.toType(CollectionUtils.getValueFromMap(args, ARG_ORIGINAL_FUNCTION_CALL), FunctionCall.class);
    final String toolName = Objects.requireNonNull(originalCall).name().orElse(null);
    return new SessionPause(resolveKind(toolName, originalCall.args().orElseThrow()), confirmationId);
  }

  private static SessionPauseKind resolveKind(final String toolName, final Map<String, Object> payload) {
    if (!HumanInTheLoopTool.TOOL_NAME.equals(toolName)) {
      return SessionPauseKind.DECISION;
    }
    return SessionPauseKind.valueOfOrDefault(CollectionUtils.getStringValueFromMap(payload, HumanInTheLoopTool.KIND));
  }
}
