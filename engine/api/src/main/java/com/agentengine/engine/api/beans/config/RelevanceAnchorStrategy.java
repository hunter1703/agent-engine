package com.agentengine.engine.api.beans.config;

import java.util.Locale;

/** Strategy used to build topic anchors for relevance checks. */
public enum RelevanceAnchorStrategy {
  /** Fallback for invalid or missing config values. */
  UNKNOWN,
  /** Anchor to several recent user messages; useful for multi-turn conversations. */
  RECENT_USER,
  /** Anchor to latest user intent plus active plan/task context; best for planning agents. */
  LATEST_USER_AND_PLAN;

  public static RelevanceAnchorStrategy valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return RelevanceAnchorStrategy.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
