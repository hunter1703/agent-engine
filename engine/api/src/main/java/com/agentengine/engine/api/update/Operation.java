package com.agentengine.engine.api.update;

import java.util.Objects;

public record Operation(String field, OperationType type, Object value) {

  public static Operation set(final String field, final Object value) {
    return new Operation(field, OperationType.SET, value);
  }

  public static Operation unset(final String field) {
    return new Operation(field, OperationType.UNSET, null);
  }
}
