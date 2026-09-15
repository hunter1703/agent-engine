package com.agentengine.util.common;

import io.vertx.json.schema.common.dsl.SchemaBuilder;
import java.util.*;
import java.util.Map.Entry;

public final class SchemaUtils {

  private SchemaUtils() {}

  @SuppressWarnings("unchecked")
  public static Map<String, Object> toMap(final SchemaBuilder<?, ?> builder) {
    final HashMap<String, Object> jsonSchemaMap =
        new HashMap<>((Map<String, Object>) builder.toJson().mapTo(Map.class));
    JsonUtils.removeValue(jsonSchemaMap, "$..['$id']");
    return jsonSchemaMap;
  }

  /**
   * Walks {@code data} guided by {@code schema}, applying {@code visitor} to every scalar leaf.
   * Containers are traversed structurally and rebuilt/replaced according to the schema; the visitor
   * never sees a Map or List.
   *
   * <p>Returns the transformed value, which may share structure with the input if the input
   * containers are mutable. Pass immutable inputs at your own risk — see below.
   */
  public static Object walk(
      final Map<String, Object> schema,
      final Object data,
      final TriFunction<Object, Map<String, Object>, Object, Object> visitor) {
    return walk(null, schema, data, visitor);
  }

  private static Object walk(
      final Object keyNode,
      final Map<String, Object> schema,
      final Object data,
      final TriFunction<Object, Map<String, Object>, Object, Object> visitor) {
    if (schema == null) {
      return data;
    }

    if (data instanceof Map<?, ?> rawMap) {
      if (!schema.containsKey("properties")) {
        return visitor.apply(keyNode, schema, data);
      }

      final Map<String, Map<String, Object>> props =
          CollectionUtils.nullSafeMap(CollectionUtils.getMapFromMap(schema, "properties"));

      final Map<String, Object> out = new LinkedHashMap<>();
      for (final Entry<String, Map<String, Object>> entry : props.entrySet()) {
        final String key = entry.getKey();
        final Map<String, Object> childSchema = entry.getValue();
        if (childSchema == null) {
          continue;
        }
        if (rawMap.containsKey(key)) {
          out.put(key, walk(key, childSchema, rawMap.get(key), visitor));
        } else if (childSchema.containsKey("default")) {
          out.put(key, walk(key, childSchema, childSchema.get("default"), visitor));
        }
      }
      return out;
    }
    if (data instanceof List<?> rawList) {
      if (!schema.containsKey("items")) {
        return visitor.apply(keyNode, schema, data);
      }

      final Map<String, Object> itemsSchema = CollectionUtils.getMapFromMap(schema, "items");
      if (itemsSchema == null) {
        return visitor.apply(keyNode, schema, data);
      }

      final List<Object> out = new ArrayList<>(rawList.size());
      for (int i = 0; i < rawList.size(); i++) {
        out.add(walk(i, itemsSchema, rawList.get(i), visitor));
      }
      return out;
    }
    return visitor.apply(keyNode, schema, data);
  }
}
