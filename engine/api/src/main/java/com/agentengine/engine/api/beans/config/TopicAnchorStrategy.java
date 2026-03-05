package com.agentengine.engine.api.beans.config;

import java.util.Locale;

/**
 * Strategy used to build topic anchors for relevance checks.
 */
public enum TopicAnchorStrategy {
  /** Fallback for invalid or missing config values. */
  UNKNOWN,
  /** Anchor to only the latest user message; fastest and strictest to last turn intent. */
  LATEST_USER,
  /** Anchor to several recent user messages; useful for multi-turn conversations. */
  RECENT_USER,
  /** Anchor to latest user intent plus active plan/task context; best for planning agents. */
  LATEST_USER_AND_PLAN;

  public static TopicAnchorStrategy valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return TopicAnchorStrategy.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
