package com.agentengine.engine.api.utils;

import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CorrectionUtils {
  public static final String CORRECTION_KEY = "correction";
  public static final String CORRECTION_TYPE_KEY = "correction_type";
  public static final String CORRECTION_CODE_KEY = "correction_code";
  public static final String CORRECTION_MESSAGE_KEY = "correction_message";

  private CorrectionUtils() {}

  public static EventActions buildCorrectionActions(
      final String type, final String code, final String message) {
    final ConcurrentHashMap<String, Object> stateDelta = new ConcurrentHashMap<>();
    stateDelta.put(CORRECTION_KEY, true);
    if (StringUtils.isNotBlank(type)) {
      stateDelta.put(CORRECTION_TYPE_KEY, type);
    }
    if (StringUtils.isNotBlank(code)) {
      stateDelta.put(CORRECTION_CODE_KEY, code);
    }
    if (StringUtils.isNotBlank(message)) {
      stateDelta.put(CORRECTION_MESSAGE_KEY, message);
    }
    return EventActions.builder().stateDelta(stateDelta).build();
  }

  public static boolean isCorrectionEvent(final Event event) {
    if (event == null || event.actions() == null) {
      return false;
    }
    return Boolean.TRUE.equals(CollectionUtils.getBooleanValueFromMap(event.actions().stateDelta(), CORRECTION_KEY));
  }

  public static CorrectionMetadata extractCorrectionMetadata(final Event event) {
    if (!isCorrectionEvent(event)) {
      return null;
    }
    final Map<String, Object> stateDelta = event.actions().stateDelta();
    final String type = CollectionUtils.getStringValueFromMap(stateDelta, CORRECTION_TYPE_KEY);
    final String code = CollectionUtils.getStringValueFromMap(stateDelta, CORRECTION_CODE_KEY);
    final String message = CollectionUtils.getStringValueFromMap(stateDelta, CORRECTION_MESSAGE_KEY);
    return new CorrectionMetadata(type, code, message);
  }
}
