package com.agentengine.engine.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.config.GuardrailRuleType;
import com.agentengine.engine.api.beans.config.GuardrailStage;
import com.agentengine.engine.api.beans.config.GuardrailsConfig;
import com.agentengine.engine.api.beans.config.TextContentGuardrailRule;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuardrailRegistryProviderSelectionTest {

  @Test
  void routesRuleToMatchingProvider() {
    final GuardrailRegistry registry = new GuardrailRegistry(List.of(new TextContentGuardrailProvider()));

    final TextContentGuardrailRule rule = new TextContentGuardrailRule();
    rule.setType(GuardrailRuleType.TEXT_CONTENT.name());
    rule.setStage(GuardrailStage.INPUT);

    final GuardrailsConfig guardrailsConfig = new GuardrailsConfig();
    guardrailsConfig.setRules(List.of(rule));

    final List<Guardrail> input = registry.getGuardrailsForStage(guardrailsConfig, GuardrailStage.INPUT);
    final List<Guardrail> output = registry.getGuardrailsForStage(guardrailsConfig, GuardrailStage.OUTPUT);

    assertThat(input).hasSize(1);
    assertThat(output).isEmpty();
  }

  @Test
  void skipsRuleWhenTypeUnknown() {
    final GuardrailRegistry registry = new GuardrailRegistry(List.of(new TextContentGuardrailProvider()));

    final TextContentGuardrailRule rule = new TextContentGuardrailRule();
    rule.setType(GuardrailRuleType.UNKNOWN.name());

    final GuardrailsConfig guardrailsConfig = new GuardrailsConfig();
    guardrailsConfig.setRules(List.of(rule));

    assertThat(registry.getGuardrailsForStage(guardrailsConfig, GuardrailStage.INPUT)).isEmpty();
  }
}
