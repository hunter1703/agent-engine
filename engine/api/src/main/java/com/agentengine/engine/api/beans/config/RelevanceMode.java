package com.agentengine.engine.api.beans.config;

import java.util.Locale;

/** Controls how response-relevance drift is handled across retries. */
public enum RelevanceMode {
  /** Fallback for invalid or missing config values. */
  UNKNOWN,
  /** Warn and request regeneration, never block solely for relevance drift. */
  STEER_ONLY,
  /** Warn up to max steering retries, then allow the response without further relevance retries. */
  STEER_THEN_ALLOW,
  /** Warn first, then block after max steering retries. */
  STEER_THEN_BLOCK;

  public static RelevanceMode valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return RelevanceMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
