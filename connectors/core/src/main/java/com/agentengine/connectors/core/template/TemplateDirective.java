package com.agentengine.connectors.core.template;

import java.util.Map;

public enum TemplateDirective {
  UNKNOWN("") ,
  EXPR("$expr"),
  TEMPLATE("$template"),
  OPTIONAL("$optional"),
  INCLUDE_IF("$includeIf");

  private final String key;

  TemplateDirective(final String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }

  public static TemplateDirective fromKey(final String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    final String normalized = value.trim();
    for (TemplateDirective directive : values()) {
      if (directive.key.equals(normalized)) {
        return directive;
      }
    }
    return UNKNOWN;
  }

  public static Map<String, Object> expr(final String expression) {
    return Map.of(EXPR.key(), expression);
  }

  public static Map<String, Object> template(final String template) {
    return Map.of(TEMPLATE.key(), template);
  }

  public static Map<String, Object> optional(final String expression) {
    return Map.of(EXPR.key(), expression, OPTIONAL.key(), true);
  }

  public static Map<String, Object> includeIf(final String conditionExpression, final Object template) {
    return Map.of(INCLUDE_IF.key(), conditionExpression, TEMPLATE.key(), template);
  }
}
