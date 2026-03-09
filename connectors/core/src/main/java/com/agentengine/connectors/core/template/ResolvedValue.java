package com.agentengine.connectors.core.template;

public record ResolvedValue(ResolvedValueStatus status, Object value) {

  public ResolvedValue {
    status = status == null ? ResolvedValueStatus.UNKNOWN : status;
  }

  public static ResolvedValue resolved(final Object value) {
    return value == null ? nullValue() : new ResolvedValue(ResolvedValueStatus.RESOLVED, value);
  }

  public static ResolvedValue nullValue() {
    return new ResolvedValue(ResolvedValueStatus.NULL_VALUE, null);
  }

  public static ResolvedValue unresolved() {
    return new ResolvedValue(ResolvedValueStatus.UNRESOLVED, null);
  }

  public static ResolvedValue omitted() {
    return new ResolvedValue(ResolvedValueStatus.OMITTED, null);
  }
}
