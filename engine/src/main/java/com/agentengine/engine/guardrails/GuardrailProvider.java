package com.agentengine.engine.guardrails;

import com.agentengine.engine.api.beans.config.GuardrailRuleConfig;
import com.agentengine.engine.api.beans.config.GuardrailRuleType;
import com.agentengine.engine.api.beans.config.GuardrailsConfig;
import java.util.List;

public interface GuardrailProvider {
  GuardrailRuleType type();

  Guardrail create(GuardrailsConfig guardrailsConfig, GuardrailRuleConfig rule);
}
