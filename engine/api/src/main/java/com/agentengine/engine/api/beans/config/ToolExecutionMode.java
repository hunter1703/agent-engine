package com.agentengine.engine.api.beans.config;

import java.util.Locale;

/**
 * Tool execution mode when multiple tools are requested in a single turn.
 */
public enum ToolExecutionMode {
  /**
   * Execute tools one at a time in the order they are requested. Use this when
   * tools have dependent side effects that must run sequentially.
   */
  SEQUENTIAL,

  /**
   * Execute tools concurrently for better performance. This is the default mode.
   */
  PARALLEL;

  /**
   * Parse a tool execution mode from a string value. Returns {@link #PARALLEL} as
   * default for null/blank/invalid values.
   */
  public static ToolExecutionMode valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return PARALLEL;
    }
    try {
      return ToolExecutionMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return PARALLEL;
    }
  }
}
