package com.agentengine.knowledge.api.beans;

import java.util.Locale;

public enum SourceType {
    UNKNOWN,
    URL,
    FILE,
    STRING;

    public static SourceType valueOfOrDefault(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return SourceType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }
}
