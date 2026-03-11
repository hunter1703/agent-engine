package com.agentengine.engine.guardrails.rules;

import com.agentengine.engine.api.beans.config.GuardrailAction;
import com.agentengine.engine.api.beans.config.GuardrailStage;
import com.agentengine.engine.api.beans.config.ToolSafetyGuardrailRule;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.ToolRiskLevel;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.engine.guardrails.Guardrail;
import com.agentengine.engine.guardrails.GuardrailConstants;
import com.agentengine.engine.guardrails.GuardrailContext;
import com.agentengine.engine.guardrails.GuardrailDecision;
import com.agentengine.engine.guardrails.GuardrailUtils;
import java.util.Map;
import java.util.Objects;

public final class ToolSafetyGuardrail implements Guardrail {
  private final ToolSafetyGuardrailRule rule;
  private final String id;

  public ToolSafetyGuardrail(final ToolSafetyGuardrailRule rule) {
    this.rule = Objects.requireNonNull(rule);
    this.id = GuardrailUtils.resolveRuleId(rule, "tool_safety");
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public GuardrailStage stage() {
    return GuardrailStage.TOOL;
  }

  @Override
  public GuardrailDecision evaluate(final GuardrailContext context) {
    final ToolDescriptor descriptor = context.toolDescriptor();
    if (descriptor == null) {
      return GuardrailDecision.allow();
    }
    final String toolName = descriptor.name();
    final var allowedToolNames = CollectionUtils.nullSafeList(rule.getToolNames());
    if (!allowedToolNames.isEmpty() && !allowedToolNames.contains(toolName)) {
      return GuardrailDecision.allow();
    }
    final ToolRiskLevel risk = descriptor.riskLevel();
    GuardrailDecision decision = GuardrailDecision.allow();
    if (ToolRiskLevel.atLeast(risk, rule.getMinToolRisk())) {
      final String message =
          rule.getMessage() != null
              ? rule.getMessage()
              : "Tool safety policy triggered for '" + descriptor.name() + "'.";
      decision =
          GuardrailUtils.fromAction(
              rule.getAction(),
              GuardrailConstants.Code.TOOL_POLICY,
              message,
              Map.of(
                  GuardrailConstants.DetailKey.TOOL, descriptor.name(),
                  GuardrailConstants.DetailKey.RISK, risk.name(),
                  GuardrailConstants.DetailKey.RULE, rule.getId()));
    }

    if (decision.action() == GuardrailAction.ALLOW
        && (risk == ToolRiskLevel.HIGH || risk == ToolRiskLevel.CRITICAL)) {
      return GuardrailDecision.escalate(
          GuardrailConstants.Code.TOOL_ESCALATE,
          "Tool '"
              + descriptor.name()
              + "' requires human confirmation due to risk level "
              + risk.name().toLowerCase()
              + ".");
    }

    return decision;
  }
}
