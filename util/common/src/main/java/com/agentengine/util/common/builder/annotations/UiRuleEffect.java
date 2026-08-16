package com.agentengine.util.common.builder.annotations;

/** Conditional effects supported by the builder contract. */
public enum UiRuleEffect {
  /** Controls whether the field is visible in the builder UI. */
  VISIBLE,
  /** Controls whether the field is editable in the builder UI. */
  ENABLED,
  /** Controls whether the field is required in the builder UI. */
  REQUIRED
}
