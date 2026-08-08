package com.agentengine.util.scripts.templated;

import java.util.List;
import java.util.Map;

public class JoinTemplateImpl implements Template<String> {

    private final List<Template<?>> templates;

    public JoinTemplateImpl(List<Template<?>> templates) {
        this.templates = templates;
    }

    @Override
    public String getValue(Map<String, Object> parameters) {
        final StringBuilder result = new StringBuilder();
        for (final Template<?> template : templates) {
            result.append(template.getValue(parameters));
        }
        return result.toString();
    }
}
