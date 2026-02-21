package com.agentengine.engine.api.query;

public enum Operator {
  AND(true),
  NOT(true),
  OR(true),
  EQ,
  NE,
  GT,
  GTE,
  LT,
  LTE,
  LIKE,
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
}
