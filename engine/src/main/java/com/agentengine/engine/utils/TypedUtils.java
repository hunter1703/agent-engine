package com.agentengine.engine.utils;

import com.agentengine.util.common.JsonUtils;
import java.util.Map;

public final class TypedUtils {

  private TypedUtils() {}

  public static <T> T toType(final Object value, final Class<T> type) {
    if (value == null || type == null) {
      return null;
    }
    if (type.isInstance(value)) {
      return type.cast(value);
    }
    if (value instanceof Map<?, ?> rawMap) {
      @SuppressWarnings("unchecked")
      final Map<String, Object> mapValue = (Map<String, Object>) rawMap;
      return JsonUtils.fromMap(mapValue, type);
    }
    return null;
  }
}
