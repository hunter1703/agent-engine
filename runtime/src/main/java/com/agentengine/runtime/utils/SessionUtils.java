package com.agentengine.runtime.utils;

import com.agentengine.util.agents.beans.session.SessionInfo;
import com.agentengine.runtime.hitl.SessionPause;
import com.agentengine.runtime.hitl.SessionPauseKind;
import com.agentengine.runtime.tools.HumanInTheLoopTool;
import com.agentengine.runtime.utils.ToolUtils;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.Utils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.Functions;
import com.google.adk.sessions.Session;
import com.google.genai.types.FunctionCall;

import java.time.Instant;
import java.util.*;
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
    final FunctionCall originalCall = Utils.toType(CollectionUtils.getValueFromMap(args, ARG_ORIGINAL_FUNCTION_CALL),
        new TypeReference<>() {
        });
    final String toolName = Objects.requireNonNull(originalCall).name().orElse(null);
    return new SessionPause(resolveKind(toolName, originalCall.args().orElseThrow()), confirmationId);
  }

  private static SessionPauseKind resolveKind(final String toolName, final Map<String, Object> payload) {
    if (!HumanInTheLoopTool.TOOL_NAME.equals(toolName)) {
      return SessionPauseKind.DECISION;
    }
    return SessionPauseKind.valueOfOrDefault(CollectionUtils.getStringValueFromMap(payload, HumanInTheLoopTool.KIND));
  }

  public static SessionInfo toSessionInfo(final Session session) {
    final SessionInfo sessionInfo = new SessionInfo();
    sessionInfo.setId(session.id());
    sessionInfo.setAppName(session.appName());
    sessionInfo.setUserId(session.userId());
    sessionInfo.setState(session.state() == null ? new HashMap<>() : new HashMap<>(session.state()));

    if (session.events() != null) {
      final List<Map<String, Object>> eventMaps = new ArrayList<>();
      for (final Event event : session.events()) {
        eventMaps.add(JsonUtils.toMap(event));
      }
      sessionInfo.setEvents(eventMaps);
    } else {
      sessionInfo.setEvents(new ArrayList<>());
    }

    sessionInfo.setLastUpdateTime(session.lastUpdateTime().toEpochMilli());
    return sessionInfo;
  }

  public static Session toSession(final SessionInfo sessionInfo) {
    final ConcurrentMap<String, Object> sessionState = new ConcurrentHashMap<>(CollectionUtils.nullSafeMap(sessionInfo.getState()));
    final Session.Builder builder = Session.builder(sessionInfo.getId()).appName(sessionInfo.getAppName()).userId(sessionInfo.getUserId())
        .state(sessionState).events(JsonUtils.fromJson(sessionInfo.getEventsJson(), new TypeReference<>() {
        }));
    builder.lastUpdateTime(Instant.ofEpochMilli(sessionInfo.getLastUpdateTime()));
    return builder.build();
  }
}
