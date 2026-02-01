package com.agentengine.engine.api.beans.session;

public enum PlanStatus {
  TODO("todo"), IN_PROGRESS("in_progress"), DONE("done"), ABANDONED("abandoned");

  private final String value;

  PlanStatus(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
