package com.agentengine.engine.api.utils;

import java.util.*;
import java.util.function.Function;

public final class CollectionUtils {

  private CollectionUtils() {
  }

  public static <K, V> Map<K, V> nullSafeMap(final Map<K, V> map) {
    if (map == null) {
      return Collections.emptyMap();
    }
    return map;
  }

  public static <T> List<T> nullSafeList(final Collection<T> collection) {
    if (CollectionUtils.isEmpty(collection)) {
      return List.of();
    }
    return List.copyOf(collection);
  }

  public static <T> List<T> nullSafeMutableList(final Collection<T> collection) {
    if (CollectionUtils.isEmpty(collection)) {
      return List.of();
    }
    return new ArrayList<>(collection);
  }

  public static String getStringValueFromMap(final Map<String, Object> map, final String key) {
    if (CollectionUtils.isEmpty(map)) {
      return null;
    }
    return (String) map.get(key);
  }

  public static <T> T getFirst(final Collection<T> collection) {
    if (CollectionUtils.isEmpty(collection)) {
      return null;
    }
    return collection.iterator().next();
  }

  public static Long getLongValueFromMap(final Map<String, Object> map, final String key) {
    if (CollectionUtils.isEmpty(map)) {
      return null;
    }
    final Object value = map.get(key);
    return switch (value) {
      case Number number -> number.longValue();
      default -> Long.parseLong(value.toString());
    };
  }

  public static Boolean getBooleanValueFromMap(final Map<String, Object> map, final String key) {
    if (CollectionUtils.isEmpty(map)) {
      return null;
    }
    final Object value = map.get(key);
    if (value instanceof Boolean bool) {
      return bool;
    }
    return value == null ? null : Boolean.parseBoolean(value.toString());
  }

  @SuppressWarnings("unchecked")
  public static <K, V> Map<K, V> getMapFromMap(final Map<String, Object> map, final String key) {
    if (CollectionUtils.isEmpty(map)) {
      return null;
    }
    return (Map<K, V>) map.get(key);
  }

  public static boolean isEmpty(final Map<?, ?> map) {
    return map == null || map.isEmpty();
  }

  public static boolean isEmpty(final Collection<?> collection) {
    return collection == null || collection.isEmpty();
  }

  public static boolean isNotEmpty(final Collection<?> collection) {
    return !isEmpty(collection);
  }

  public static boolean isNotEmpty(final Map<?, ?> map) {
    return !isEmpty(map);
  }

  public static <T, K, V> Map<K, V> transformToMultiKeyMap(final Collection<T> collection,
      final Function<T, ? extends Collection<K>> keysFunction, final Function<T, V> valueFunction) {
    final Map<K, V> map = new HashMap<>();
    for (final T item : collection) {
      final Collection<K> keys = keysFunction.apply(item);
      if (CollectionUtils.isEmpty(keys)) {
        continue;
      }
      final V value = valueFunction.apply(item);

      for (final K key : keys) {
        map.put(key, value);
      }
    }

    return map;
  }

  public static <T, K, V> Map<K, V> transformToMap(final Collection<T> collection, final Function<T, K> keyFunction,
      final Function<T, V> valueFunction) {
    final Map<K, V> map = new HashMap<>();
    for (final T item : collection) {
      final K key = keyFunction.apply(item);
      if (key == null) {
        continue;
      }
      final V value = valueFunction.apply(item);
      map.put(key, value);
    }

    return map;
  }
}
