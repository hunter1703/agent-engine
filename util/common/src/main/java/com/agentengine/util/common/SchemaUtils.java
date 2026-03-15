package com.agentengine.util.common;

import io.vertx.json.schema.common.dsl.SchemaBuilder;
import java.util.HashMap;
import java.util.Map;

public final class SchemaUtils {

  private SchemaUtils() {
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> toMap(final SchemaBuilder<?, ?> builder) {
    final HashMap<String, Object> jsonSchemaMap = new HashMap<>((Map<String, Object>) builder.toJson().mapTo(Map.class));
    JsonUtils.removeValue(jsonSchemaMap, "$..['$id']");
    return jsonSchemaMap;
  }
}
