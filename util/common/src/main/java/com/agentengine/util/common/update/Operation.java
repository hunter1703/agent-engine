package com.agentengine.util.common.update;

import java.util.Collection;

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

  public static Operation addToSet(final String field, final Collection<?> values) {
    return new Operation(field, OperationType.ADD_TO_SET, values);
  }

  public static Operation inc(final String field, final Number value) {
    return new Operation(field, OperationType.INC, value);
  }

  public static Operation removeFromSet(final String field, final Collection<?> values) {
    return new Operation(field, OperationType.REMOVE_FROM_SET, values);
  }

  public static Operation setOnInsert(final String field, final Object value) {
    return new Operation(field, OperationType.SET_ON_INSERT, value);
  }
}
