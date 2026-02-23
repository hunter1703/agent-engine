package com.agentengine.engine.tools.planning.beans;

public enum TaskStatus {
  TODO("todo"),
  IN_PROGRESS("in_progress"),
  DONE("done"),
  ABANDONED("abandoned");

  private final String value;

  TaskStatus(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
