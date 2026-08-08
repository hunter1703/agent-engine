package com.agentengine.util.scripts.templated;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapTemplateImpl<K, V> implements Template<Map<K, V>> {

    private final List<Map.Entry<Template<K>, Template<V>>> entries;

    public MapTemplateImpl(List<Map.Entry<Template<K>, Template<V>>> entries) {
        this.entries = entries;
    }

    @Override
    public Map<K, V> getValue(Map<String, Object> parameters) {
        final Map<K, V> result = new HashMap<>();
        for (final Map.Entry<Template<K>, Template<V>> entry : entries) {
            final K key = entry.getKey().getValue(parameters);
            final V value = entry.getValue().getValue(parameters);
            if (key != null && value != null) {
                result.put(key, value);
            }
        }
        return result;
    }
}
