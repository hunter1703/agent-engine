package com.agentengine.connectors.core.config;

import java.util.Locale;

public enum HttpMethod {
    UNKNOWN,
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
    HEAD,
    OPTIONS;

    public static HttpMethod valueOfOrDefault(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return HttpMethod.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }

    public boolean supportsBody() {
        return switch (this) {
            case POST, PUT, PATCH -> true;
            default -> false;
        };
    }
}
