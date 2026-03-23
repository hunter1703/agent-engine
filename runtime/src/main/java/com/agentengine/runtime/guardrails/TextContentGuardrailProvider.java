package com.agentengine.runtime.guardrails;

import com.agentengine.util.agents.beans.config.GuardrailRuleType;
import com.agentengine.util.agents.beans.config.TextContentGuardrailRule;
import com.agentengine.runtime.guardrails.TextContentGuardrail;
import jakarta.inject.Singleton;

@Singleton
public final class TextContentGuardrailProvider implements GuardrailProvider<TextContentGuardrailRule> {

  @Override
  public GuardrailRuleType type() {
    return GuardrailRuleType.TEXT_CONTENT;
  }

  @Override
  public Guardrail create(final TextContentGuardrailRule rule) {
    if (rule == null) {
      return null;
    }
    return new TextContentGuardrail(rule);
  }
}
