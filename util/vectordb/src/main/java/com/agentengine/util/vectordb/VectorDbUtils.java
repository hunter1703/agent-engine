package com.agentengine.util.vectordb;

import io.qdrant.client.ValueFactory;
import io.qdrant.client.grpc.JsonWithInt.Value;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VectorDbUtils {

  private VectorDbUtils() {}

  public static String clientId(final String store, final Integer customerId) {
    return VectorClientInfraConfig.TYPE + ":" + store + ":" + customerId;
  }

  static Map<String, Value> toValues(final Map<String, Object> payload) {
    final Map<String, Value> values = new LinkedHashMap<>();
    payload.forEach((key, value) -> values.put(key, toValue(value)));
    return values;
  }

  static Map<String, Object> fromValues(final Map<String, Value> values) {
    final Map<String, Object> payload = new LinkedHashMap<>();
    values.forEach((key, value) -> payload.put(key, fromValue(value)));
    return payload;
  }

  private static Value toValue(final Object value) {
    return switch (value) {
      case null -> ValueFactory.nullValue();
      case String string -> ValueFactory.value(string);
      case Boolean bool -> ValueFactory.value(bool);
      case Byte number -> ValueFactory.value(number.longValue());
      case Short number -> ValueFactory.value(number.longValue());
      case Integer number -> ValueFactory.value(number.longValue());
      case Long number -> ValueFactory.value(number);
      case Number number -> ValueFactory.value(number.doubleValue());
      case Collection<?> collection -> ValueFactory.list(toList(collection));
      case Map<?, ?> map -> {
        final Map<String, Value> struct = new LinkedHashMap<>();
        map.forEach((key, entry) -> struct.put(String.valueOf(key), toValue(entry)));
        yield ValueFactory.value(struct);
      }
      default -> ValueFactory.value(value.toString());
    };
  }

  private static List<Value> toList(final Collection<?> collection) {
    final List<Value> values = new ArrayList<>(collection.size());
    for (final Object element : collection) {
      values.add(toValue(element));
    }
    return values;
  }

  private static Object fromValue(final Value value) {
    return switch (value.getKindCase()) {
      case STRING_VALUE -> value.getStringValue();
      case INTEGER_VALUE -> value.getIntegerValue();
      case DOUBLE_VALUE -> value.getDoubleValue();
      case BOOL_VALUE -> value.getBoolValue();
      case STRUCT_VALUE -> fromValues(value.getStructValue().getFieldsMap());
      case LIST_VALUE ->
          value.getListValue().getValuesList().stream().map(VectorDbUtils::fromValue).toList();
      case NULL_VALUE, KIND_NOT_SET -> null;
    };
  }
}
