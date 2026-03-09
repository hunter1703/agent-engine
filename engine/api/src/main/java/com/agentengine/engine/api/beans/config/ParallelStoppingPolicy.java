package com.agentengine.engine.api.beans.config;

import java.util.Locale;

/**
 * Early-stopping strategy for parallel branch execution.
 */
public enum ParallelStoppingPolicy {
  /** Fallback for invalid or missing config values. */
  UNKNOWN,
  /** Wait for all branches to complete unless timeout occurs. */
  ALL_COMPLETE,
  /** Stop once the first successful branch completes. */
  FIRST_SUCCESS,
  /** Stop once a configurable number of successful branches is reached. */
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
