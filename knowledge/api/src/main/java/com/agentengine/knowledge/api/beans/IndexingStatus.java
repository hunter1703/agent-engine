package com.agentengine.knowledge.api.beans;

import java.util.Locale;

public enum IndexingStatus {
    UNKNOWN,
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED;

    public static IndexingStatus valueOfOrDefault(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return IndexingStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }
}
