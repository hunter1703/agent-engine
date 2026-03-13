package com.agentengine.engine.api.beans.config;

import java.util.Locale;

/** Aggregation strategy for merging outputs from parallel branches. */
public enum ParallelAggregationPolicy {
  /** Fallback for invalid or missing config values. */
  UNKNOWN,
  /** Preserve all successful outputs and concatenate them in branch order. */
  CONCATENATE,
  /**
   * Pick a single best-looking successful output (currently longest output
   * heuristic).
   */
  BEST_EFFORT,
  /** Pick output supported by majority of matching normalized responses. */
  MAJORITY_VOTE;

  public static ParallelAggregationPolicy valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return ParallelAggregationPolicy.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
