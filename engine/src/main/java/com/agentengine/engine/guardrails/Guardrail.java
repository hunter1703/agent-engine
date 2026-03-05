package com.agentengine.engine.guardrails;

import com.agentengine.engine.api.beans.config.GuardrailStage;

public interface Guardrail {
  String id();

  GuardrailStage stage();

  GuardrailDecision evaluate(GuardrailContext context);
}
