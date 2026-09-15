package com.agentengine.util.common;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class CollectionUtils {

  private CollectionUtils() {}

  public static <T> Set<T> union(final Collection<T> one, final Collection<T> two) {
    final Set<T> setOne = CollectionUtils.nullSafeMutableSet(one);
    final Set<T> setTwo = CollectionUtils.nullSafeMutableSet(two);
    setOne.addAll(setTwo);
    return setOne;
  }

  public static <T> List<T> append(final List<T> one, final List<T> toAppend) {
    final List<T> newList = new ArrayList<>();
    newList.addAll(CollectionUtils.nullSafeList(one));
    newList.addAll(CollectionUtils.nullSafeList(toAppend));
    return newList;
  }

  @SafeVarargs
  public static <T> List<T> append(final List<T> one, final List<T> two, final T... items) {
    final List<T> newList = new ArrayList<>();
    newList.addAll(CollectionUtils.nullSafeList(one));
    newList.addAll(CollectionUtils.nullSafeList(two));
    newList.addAll(items == null ? List.of() : List.of(items));
    return newList;
  }

  public static <T> List<T> append(final List<T> one, final T element, final List<T> toAppend) {
    final List<T> newList = new ArrayList<>(CollectionUtils.nullSafeList(one));
    newList.add(element);
    newList.addAll(CollectionUtils.nullSafeList(toAppend));
    return newList;
  }

  @SafeVarargs
  public static <T> List<T> append(final T element, final List<T>... lists) {
    final List<T> newList = new ArrayList<>();
    newList.add(element);

    if (lists == null) {
      return newList;
    }
    for (final List<T> list : lists) {
      newList.addAll(CollectionUtils.nullSafeList(list));
    }
    return newList;
  }

  public static <T> List<T> append(final List<T> one, final T toAppend) {
    return append(one, List.of(toAppend));
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

  public static <T> Set<T> nullSafeMutableSet(final Collection<T> collection) {
    if (CollectionUtils.isEmpty(collection)) {
      return new HashSet<>();
    }
    return new HashSet<>(CollectionUtils.nullSafeList(collection));
  }

  public static <T> List<T> nullSafeMutableList(final Collection<T> collection) {
    if (CollectionUtils.isEmpty(collection)) {
      return new ArrayList<>();
    }
    return new ArrayList<>(collection);
  }

  public static <K, V> Map<K, V> nullSafeMutableMap(final Map<K, V> map) {
    if (CollectionUtils.isEmpty(map)) {
      return new HashMap<>();
    }
    return new HashMap<>(map);
  }

  public static <K> String getStringValueFromMap(final Map<K, ?> map, final K key) {
    return getStringValueFromMap(map, key, null);
  }

  public static <K> String getStringValueFromMap(
      final Map<K, ?> map, final K key, final String defaultValue) {
    if (CollectionUtils.isEmpty(map)) {
      return defaultValue;
    }
    final Object value = map.get(key);
    return value == null ? defaultValue : (value instanceof String str ? str : value.toString());
  }

  @SuppressWarnings("unchecked")
  public static <K, V> V getValueFromMap(final Map<K, ?> map, final K key) {
    if (CollectionUtils.isEmpty(map)) {
      return null;
    }
    return (V) map.get(key);
  }

  public static <T> T getValueFromMap(
      final Map<String, Object> map, final String key, final Class<T> type) {
    if (CollectionUtils.isEmpty(map) || type == null) {
      return null;
    }
    final Object value = map.get(key);
    if (type.isInstance(value)) {
      return type.cast(value);
    }
    return null;
  }

  public static <T> T getFirst(final List<T> list) {
    if (CollectionUtils.isEmpty(list)) {
      return null;
    }
    return list.getFirst();
  }

  public static <T> T getLast(final List<T> list) {
    if (CollectionUtils.isEmpty(list)) {
      return null;
    }
    return list.getLast();
  }

  public static Long getLongValueFromMap(final Map<String, Object> map, final String key) {
    if (CollectionUtils.isEmpty(map)) {
      return null;
    }
    final Object value = map.get(key);
    if (value == null) {
      return null;
    }
    return switch (value) {
      case Number number -> number.longValue();
      default -> Long.parseLong(value.toString());
    };
  }

  public static Double getDoubleValueFromMap(final Map<String, Object> map, final String key) {
    if (CollectionUtils.isEmpty(map)) {
      return null;
    }
    final Object value = map.get(key);
    if (value == null) {
      return null;
    }
    return switch (value) {
      case Number number -> number.doubleValue();
      default -> Double.parseDouble(value.toString());
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

  @SuppressWarnings("unchecked")
  public static <T> List<T> getListFromMap(final Map<String, Object> map, final String key) {
    if (CollectionUtils.isEmpty(map)) {
      return List.of();
    }
    final Object value = map.get(key);
    if (value instanceof List<?> list) {
      return (List<T>) list;
    }
    return List.of();
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

  public static <T, K, V> Map<K, V> transformToMultiKeyMap(
      final Collection<T> collection,
      final Function<T, ? extends Collection<K>> keysFunction,
      final Function<T, V> valueFunction) {
    final Map<K, V> transformedMap = new HashMap<>();
    for (final T item : collection) {
      final Collection<K> keys = keysFunction.apply(item);
      if (CollectionUtils.isEmpty(keys)) {
        continue;
      }
      final V value = valueFunction.apply(item);
      for (final K key : keys) {
        transformedMap.put(key, value);
      }
    }
    return transformedMap;
  }

  public static <T, K, V> Map<K, V> transformToMap(
      final Collection<T> collection,
      final Function<T, K> keyFunction,
      final Function<T, V> valueFunction) {
    final Map<K, V> transformedMap = new HashMap<>();
    if (CollectionUtils.isEmpty(collection)) {
      return transformedMap;
    }
    for (final T item : collection) {
      transformedMap.put(keyFunction.apply(item), valueFunction.apply(item));
    }
    return transformedMap;
  }

  public static <I, O> List<O> transformToList(
      final Collection<I> collection, final Function<I, O> function) {
    if (CollectionUtils.isEmpty(collection)) {
      return List.of();
    }
    final List<O> transformed = new ArrayList<>();
    for (final I item : collection) {
      transformed.add(function.apply(item));
    }
    return transformed;
  }

  public static <T, K, V> Map<K, List<V>> transformToMultiValuedMap(
      final Collection<T> collection,
      final Function<T, K> keyFunction,
      final Function<T, V> valueFunction) {
    final Map<K, List<V>> transformedMap = new HashMap<>();
    for (final T item : collection) {
      final K key = keyFunction.apply(item);
      if (key == null) {
        continue;
      }
      final V value = valueFunction.apply(item);
      transformedMap.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
    }
    return transformedMap;
  }

  public static Object walk(
      final Object data, final java.util.function.BiFunction<Object, Object, Object> visitor) {
    return walk(null, data, visitor);
  }

  private static Object walk(
      final Object key, final Object data, final BiFunction<Object, Object, Object> visitor) {
    switch (data) {
      case null -> {
        return null;
      }
      case Map<?, ?> rawMap -> {
        final Map<Object, Object> out = new LinkedHashMap<>();
        for (final Map.Entry<?, ?> entry : rawMap.entrySet()) {
          final Object entryKey = entry.getKey();
          out.put(entryKey, walk(entryKey, entry.getValue(), visitor));
        }
        return out;
      }
      case List<?> rawList -> {
        final List<Object> out = new ArrayList<>(rawList.size());
        for (int i = 0; i < rawList.size(); i++) {
          out.add(walk(i, rawList.get(i), visitor));
        }
        return out;
      }
      default -> {}
    }
    return visitor.apply(key, data);
  }
}
