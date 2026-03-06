package com.agentengine.engine.utils;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.google.adk.agents.InvocationContext;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

public final class HitlStateUtils {
  public static final String HITL_PAUSED = "hitl.paused";
  public static final String HITL_QUESTION = "hitl.question";
  public static final String HITL_OPTIONS_KEY = "hitl.options";
  public static final String HITL_REASON = "hitl.reason";
  public static final String HITL_INVOCATION_ID = "hitl.invocation_id";
  public static final String HITL_REQUESTED_AT = "hitl.requested_at";

  private HitlStateUtils() {}

  public static void pause(
      final InvocationContext context, final String question, final String reason) {
    pause(context, question, List.of(), reason);
  }

  public static void pause(
      final InvocationContext context,
      final String question,
      final List<String> options,
      final String reason) {
    final ConcurrentMap<String, Object> state = state(context);
    if (state == null) {
      return;
    }
    state.put(HITL_PAUSED, true);
    if (StringUtils.isNotBlank(question)) {
      state.put(HITL_QUESTION, question);
    }
    if (StringUtils.isNotBlank(reason)) {
      state.put(HITL_REASON, reason);
    }
    final List<String> sanitized =
        options == null
            ? List.of()
            : options.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
    if (!sanitized.isEmpty()) {
      state.put(HITL_OPTIONS_KEY, sanitized);
    }
    if (context.invocationId() != null) {
      state.put(HITL_INVOCATION_ID, context.invocationId());
    }
    state.put(HITL_REQUESTED_AT, Instant.now().toEpochMilli());
  }

  public static void resume(final InvocationContext context) {
    final ConcurrentMap<String, Object> state = state(context);
    if (state == null) {
      return;
    }
    state.remove(HITL_PAUSED);
    state.remove(HITL_QUESTION);
    state.remove(HITL_OPTIONS_KEY);
    state.remove(HITL_REASON);
    state.remove(HITL_INVOCATION_ID);
    state.remove(HITL_REQUESTED_AT);
  }

  public static boolean isPaused(final InvocationContext context) {
    final ConcurrentMap<String, Object> state = state(context);
    return state != null && Boolean.TRUE.equals(state.get(HITL_PAUSED));
  }

  public static String getQuestion(final InvocationContext context) {
    final ConcurrentMap<String, Object> state = state(context);
    if (state == null) {
      return null;
    }
    return CollectionUtils.getStringValueFromMap(state, HITL_QUESTION);
  }

  public static List<String> getOptions(final InvocationContext context) {
    final ConcurrentMap<String, Object> state = state(context);
    if (state == null) {
      return List.of();
    }
    return CollectionUtils.getListFromMap(state, HITL_OPTIONS_KEY);
  }

  private static ConcurrentMap<String, Object> state(final InvocationContext context) {
    if (context == null || context.session() == null || context.session().state() == null) {
      return null;
    }
    return context.session().state();
  }
}
