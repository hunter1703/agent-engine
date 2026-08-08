package com.agentengine.util.scripts.templated;

import com.agentengine.util.scripts.TemplateUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TemplatedList<T> implements TemplatedType<List<T>> {
    private final List<Template<T>> items;

    public TemplatedList(List<?> list) {
        Objects.requireNonNull(list, "Non-null list of templates expected");
        this.items = toTemplateItems(list);
    }

    @Override
    public Template<List<T>> build() {
        return new ListTemplateImpl<>(items);
    }

    private List<Template<T>> toTemplateItems(final List<?> list) {
        final List<Template<T>> result = new ArrayList<>();
        for (final Object item : list) {
            result.add(TemplateUtils.buildTemplate(item));
        }
        return result;
    }
}
