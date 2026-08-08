package com.agentengine.util.scripts.templated;

import java.util.Map;

public class StringTemplate implements Template<String> {
    private final String value;

    public StringTemplate(String value) {
        this.value = value;
    }

    @Override
    public String getValue(Map<String, Object> parameters) {
        return value;
    }
}
