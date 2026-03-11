package com.agentengine.engine.guardrails;

import com.agentengine.engine.api.beans.config.GuardrailRuleType;
import com.agentengine.engine.api.beans.config.GuardrailStage;
import com.agentengine.engine.api.beans.config.TextContentGuardrailRule;
import com.agentengine.engine.guardrails.rules.TextContentGuardrail;
import jakarta.inject.Singleton;

@Singleton
public final class TextContentGuardrailProvider
    implements GuardrailProvider<TextContentGuardrailRule> {

  @Override
  public GuardrailRuleType type() {
    return GuardrailRuleType.TEXT_CONTENT;
  }

  @Override
  public Guardrail create(final TextContentGuardrailRule rule) {
    if (rule == null || rule.getStage() == GuardrailStage.TOOL) {
      return null;
    }
    return new TextContentGuardrail(rule);
  }
}
