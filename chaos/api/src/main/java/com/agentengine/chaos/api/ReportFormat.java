package com.agentengine.chaos.api;

import java.util.Locale;

public enum ReportFormat {
  UNKNOWN,
  JSON,
  MARKDOWN,
  HTML;

  public static ReportFormat valueOfOrDefault(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return ReportFormat.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return UNKNOWN;
    }
  }
}
