package com.localagent.engine.utils;

import java.util.*;
import java.util.function.Function;

public final class CollectionUtils {

    private CollectionUtils(){}

    public static <T> List<T> nullSafeList(final Collection<T> collection) {
        if (CollectionUtils.isEmpty(collection)) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(collection));
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

    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }

    public static <T, K, V> Map<K, V> transformToMap(Collection<T> collection, Function<T, ? extends Collection<K>> keysFunction, Function<T, V> valueFunction) {
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

    public static <T, K, V> Map<K, V> transformToMap(Collection<T> collection, Function<T, K> keyFunction, Function<T, V> valueFunction) {
        final Map<K, V> map = new HashMap<>();
        for (final T item : collection) {
            final K key = keyFunction.apply(item);
            if (key == null) {
                continue;
            }
            final V value = valueFunction.apply(item);
            map.put(key, value)
        }

        return map;
    }
}
