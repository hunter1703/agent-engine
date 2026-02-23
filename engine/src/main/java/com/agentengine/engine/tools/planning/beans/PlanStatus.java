package com.agentengine.engine.tools.planning.beans;

public enum PlanStatus {
  IN_PROGRESS("in_progress"),
  DONE("done"),
  ABANDONED("abandoned");

  private final String value;

  PlanStatus(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
