package com.agentengine.chaos.api;

import java.util.Locale;

public enum BlastRadiusScope {
    UNKNOWN,
    SINGLE_POD,
    SERVICE,
    NAMESPACE,
    CLUSTER;

    public static BlastRadiusScope valueOfOrDefault(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return BlastRadiusScope.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }
}
