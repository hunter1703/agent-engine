package com.agentengine.util.vectordb;

import com.agentengine.util.common.Utils;
import com.agentengine.util.common.annotations.Indexed;
import com.agentengine.util.infra.ClientType;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.grpc.JsonWithInt.Value;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VectorDbUtils {

  private static final String DEFAULT_VECTOR_SUFFIX = "Vector";

  private VectorDbUtils() {}

  public static VectorClientInfraConfig clientConfig(
      final VectorStoreClientType clientType, final Integer customerId, final String serverId) {
    final VectorClientInfraConfig clientConfig = new VectorClientInfraConfig();
    clientConfig.setStore(clientType.name());
    clientConfig.setCustomerId(customerId);
    clientConfig.setServerId(serverId);
    return clientConfig;
  }

  public static String clientId(final String store, final Integer customerId) {
    return ClientType.VECTOR_CLIENT + ":" + store + ":" + customerId;
  }

  /** The vector name of each {@link Indexed} vector field of the entity class, keyed by field. */
  public static Map<String, String> vectorNames(final Class<?> entityClass) {
    final Map<String, String> fieldVsVectorName = new LinkedHashMap<>();
    for (final Field field : Utils.fieldsAnnotatedWith(entityClass, Indexed.class)) {
      final Indexed declaration = field.getAnnotation(Indexed.class);
      if (declaration.vector()) {
        fieldVsVectorName.put(
            field.getName(),
            declaration.name().isBlank()
                ? field.getName() + DEFAULT_VECTOR_SUFFIX
                : declaration.name());
      }
    }
    return fieldVsVectorName;
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
