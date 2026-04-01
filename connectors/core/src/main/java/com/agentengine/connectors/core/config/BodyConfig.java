package com.agentengine.connectors.core.config;

public record BodyConfig(String type, Object template, String contentType, boolean omitNulls) {

    public BodyConfig {
        type = type == null || type.isBlank() ? BodyType.UNKNOWN.name() : type;
        omitNulls = omitNulls;
    }

    public BodyConfig(final BodyType type, final Object template, final String contentType, final boolean omitNulls) {
        this(type == null ? null : type.name(), template, contentType, omitNulls);
    }

    public BodyType typeEnum() {
        return BodyType.valueOfOrDefault(type);
    }
}
