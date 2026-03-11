package com.agentengine.engine.guardrails;

import com.agentengine.engine.api.beans.config.GuardrailRuleType;
import com.agentengine.engine.api.beans.config.GuardrailStage;
import com.agentengine.engine.api.beans.config.ToolSafetyGuardrailRule;
import com.agentengine.engine.guardrails.rules.ToolSafetyGuardrail;
import jakarta.inject.Singleton;

@Singleton
public final class ToolSafetyGuardrailProvider
    implements GuardrailProvider<ToolSafetyGuardrailRule> {

  @Override
  public GuardrailRuleType type() {
    return GuardrailRuleType.TOOL_SAFETY;
  }

  @Override
  public Guardrail create(final ToolSafetyGuardrailRule rule) {
    if (rule == null || rule.stageEnum() != GuardrailStage.TOOL) {
      return null;
    }
    return new ToolSafetyGuardrail(rule);
  }
}
