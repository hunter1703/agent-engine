package com.agentengine.chaos.api.fault;

import java.util.Locale;

public enum DatabaseTarget {
    UNKNOWN,
    POSTGRESQL,
    MONGODB;

    public static DatabaseTarget valueOfOrDefault(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return DatabaseTarget.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }
}
