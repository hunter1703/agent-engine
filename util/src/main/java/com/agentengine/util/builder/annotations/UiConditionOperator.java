package com.agentengine.util.builder.annotations;

/**
 * Comparison operators supported by conditional builder rules.
 */
public enum UiConditionOperator {
  /** Field value must equal one of the provided values. */
  EQ,
  /** Field value must not equal any of the provided values. */
  NE,
  /** Field value must be in the provided set. */
  IN,
  /** Field value must not be in the provided set. */
  NOT_IN
}
