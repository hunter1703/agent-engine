package com.agentengine.util.scripts.templated;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.scripts.TemplateUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TemplatedMap<K, V> implements TemplatedType<Map<K, V>> {

    private final List<Map.Entry<Template<K>, Template<V>>> entries;

    public TemplatedMap(Map<?, ?> rawMap) {
        this.entries = toTemplateEntries(CollectionUtils.nullSafeMap(rawMap));
    }

    @Override
    public Template<Map<K, V>> build() {
        return new MapTemplateImpl<>(entries);
    }

    private List<Map.Entry<Template<K>, Template<V>>> toTemplateEntries(final Map<?, ?> rawMap) {
        final List<Map.Entry<Template<K>, Template<V>>> result = new ArrayList<>();
        for (final Map.Entry<?, ?> entry : rawMap.entrySet()) {
            final Template<K> key = TemplateUtils.buildTemplate(entry.getKey());
            final Template<V> value = TemplateUtils.buildTemplate(entry.getValue());
            if (key == null || value == null) {
                continue;
            }
            result.add(Map.entry(key, value));
        }
        return result;
    }
}
