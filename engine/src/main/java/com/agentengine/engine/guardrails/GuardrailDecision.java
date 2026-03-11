package com.agentengine.engine.guardrails;

import com.agentengine.engine.api.beans.config.GuardrailAction;
import com.agentengine.util.common.CollectionUtils;
import java.util.Map;

public record GuardrailDecision(
    GuardrailAction action, String code, String message, Map<String, Object> details) {

  public GuardrailDecision {
    action = action == null ? GuardrailAction.ALLOW : action;
    details = CollectionUtils.nullSafeMap(details);
  }

  public static GuardrailDecision allow() {
    return new GuardrailDecision(
        GuardrailAction.ALLOW, GuardrailConstants.Code.ALLOW, "Allowed", Map.of());
  }

  public static GuardrailDecision warn(final String code, final String message) {
    return new GuardrailDecision(GuardrailAction.WARN, code, message, Map.of());
  }

  public static GuardrailDecision block(final String code, final String message) {
    return new GuardrailDecision(GuardrailAction.BLOCK, code, message, Map.of());
  }

  public static GuardrailDecision escalate(final String code, final String message) {
    return new GuardrailDecision(GuardrailAction.ESCALATE, code, message, Map.of());
  }

  public boolean isBlocking() {
    return action == GuardrailAction.BLOCK || action == GuardrailAction.ESCALATE;
  }

  public static GuardrailDecision merge(
      final GuardrailDecision left, final GuardrailDecision right) {
    final GuardrailDecision lhs = left == null ? GuardrailDecision.allow() : left;
    final GuardrailDecision rhs = right == null ? GuardrailDecision.allow() : right;
    return severity(rhs.action()) > severity(lhs.action()) ? rhs : lhs;
  }

  private static int severity(final GuardrailAction action) {
    return switch (action) {
      case ESCALATE -> 4;
      case BLOCK -> 3;
      case WARN -> 2;
      case ALLOW, UNKNOWN -> 1;
    };
  }
}
