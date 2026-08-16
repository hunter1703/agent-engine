package com.agentengine.util.agents.beans.config;

import java.util.Locale;

/** Early-stopping strategy for parallel branch execution. */
public enum ParallelStoppingPolicy {
  /** Fallback for invalid or missing config values. */
  UNKNOWN,
  /** Wait for all branches to complete unless timeout occurs. */
  ALL_COMPLETE,
  /**
   * Stop once a configurable number of successful branches is reached. To stop after the first
   * success, set quorum to 1.
   */
  QUORUM;

  public static ParallelStoppingPolicy valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return ParallelStoppingPolicy.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
