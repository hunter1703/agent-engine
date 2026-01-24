package com.agentengine.engine.api.beans.session;

public enum PlanStatus {
  PENDING,
  IN_PROGRESS,
  COMPLETED;

  public static PlanStatus fromString(final String value) {
    if (value == null || value.isBlank()) {
      return PENDING;
    }
    final String normalized = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
    for (PlanStatus status : values()) {
      if (status.name().equals(normalized)) {
        return status;
      }
    }
    return PENDING;
  }
}
