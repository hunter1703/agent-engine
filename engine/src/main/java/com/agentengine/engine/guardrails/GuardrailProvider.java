package com.agentengine.engine.guardrails;

import com.agentengine.engine.api.beans.config.GuardrailRule;
import com.agentengine.engine.api.beans.config.GuardrailRuleType;
import com.agentengine.engine.api.beans.config.GuardrailsConfig;

public interface GuardrailProvider<T extends GuardrailRule> {
  GuardrailRuleType type();

  Guardrail create(GuardrailsConfig guardrailsConfig, T rule);
}
