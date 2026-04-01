package com.agentengine.util.common.query;

import java.util.Locale;

public enum Operator {
    UNKNOWN(false),
    AND(true),
    NOT(true),
    OR(true),
    EQ,
    NE,
    GT,
    GTE,
    LT,
    LTE,
    IN,
    NIN,
    CONTAINS,
    EXISTS,
    NOT_EXISTS;

    private final boolean compound;

    Operator() {
        this(false);
    }

    Operator(boolean compound) {
        this.compound = compound;
    }

    public boolean isCompound() {
        return compound;
    }

    public static Operator valueOfOrDefault(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return Operator.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }
}
