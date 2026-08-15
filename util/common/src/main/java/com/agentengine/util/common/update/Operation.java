package com.agentengine.util.common.update;

public record Operation(String field, OperationType type, Object value) {

    public Operation {
        if (field == null) {
            throw new NullPointerException("field cannot be null");
        }
        if (type == null) {
            throw new NullPointerException("type cannot be null");
        }
    }

    public static Operation set(final String field, final Object value) {
        return new Operation(field, OperationType.SET, value);
    }

    public static Operation unset(final String field) {
        return new Operation(field, OperationType.UNSET, null);
    }

    public static Operation inc(final String field, final Number value) {
        return new Operation(field, OperationType.INC, value);
    }
}
