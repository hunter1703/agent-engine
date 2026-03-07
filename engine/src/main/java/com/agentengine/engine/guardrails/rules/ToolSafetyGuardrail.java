package com.agentengine.engine.guardrails.rules;

import com.agentengine.engine.api.beans.config.GuardrailAction;
import com.agentengine.engine.api.beans.config.GuardrailStage;
import com.agentengine.engine.api.beans.config.ToolSafetyGuardrailRule;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.ToolRiskLevel;
import com.agentengine.engine.guardrails.Guardrail;
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
    final ToolRiskLevel risk = descriptor.riskLevel();
    GuardrailDecision decision = GuardrailDecision.allow();
    if (ToolRiskLevel.atLeast(risk, rule.getMinToolRisk())) {
      final String message =
              rule.getMessage() != null
                      ? rule.getMessage()
                      : "Tool safety policy triggered for '" + descriptor.name() + "'.";
      decision = GuardrailUtils.fromAction(
              rule.getAction(),
              "guardrail_tool_policy",
              message,
              Map.of("tool", descriptor.name(), "risk", risk.name(), "rule", rule.getId()));
    }

    if (decision.action() == GuardrailAction.ALLOW
        && (risk == ToolRiskLevel.HIGH || risk == ToolRiskLevel.CRITICAL)) {
      return GuardrailDecision.escalate(
          "guardrail_tool_escalate",
          "Tool '" + descriptor.name() + "' requires human confirmation due to risk level "
              + risk.name().toLowerCase()
              + ".");
    }

    return decision;
  }

}
