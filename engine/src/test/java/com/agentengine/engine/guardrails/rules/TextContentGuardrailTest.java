package com.agentengine.engine.guardrails.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.config.GuardrailStage;
import com.agentengine.engine.api.beans.config.TextContentGuardrailRule;
import com.agentengine.engine.guardrails.GuardrailContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextContentGuardrailTest {

  @Test
  void shouldMatchBlockedPatternIgnoringCase() {
    final TextContentGuardrailRule rule = new TextContentGuardrailRule();
    rule.setId("case-insensitive-output");
    rule.setStage(GuardrailStage.OUTPUT.name());
    rule.setAction(com.agentengine.engine.api.beans.config.GuardrailAction.ESCALATE.name());
    rule.setBlockedPatterns(List.of("hello"));

    final TextContentGuardrail guardrail = new TextContentGuardrail(rule);

    assertThat(guardrail.evaluate(new GuardrailContext("Hello from the model", null)).action())
        .isEqualTo(com.agentengine.engine.api.beans.config.GuardrailAction.ESCALATE);
  }
}
