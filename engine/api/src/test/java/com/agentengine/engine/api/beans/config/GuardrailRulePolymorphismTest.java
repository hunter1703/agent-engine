package com.agentengine.engine.api.beans.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.agentengine.engine.api.utils.JsonUtils;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuardrailRulePolymorphismTest {

  @Test
  void deserializesTextContentSubtypeByType() {
    final String json =
        """
        {
          "id": "r1",
          "type": "TEXT_CONTENT",
          "enabled": true,
          "action": "BLOCK",
          "maxTextLength": 64,
          "blockedPatterns": ["ignore previous instructions"]
        }
        """;

    final GuardrailRule rule = JsonUtils.fromJson(json, GuardrailRule.class);

    assertThat(rule).isInstanceOf(TextContentGuardrailRule.class);
    final TextContentGuardrailRule typed = (TextContentGuardrailRule) rule;
    assertThat(typed.getMaxTextLength()).isEqualTo(64);
    assertThat(typed.getBlockedPatterns()).containsExactly("ignore previous instructions");
  }

  @Test
  void failsForUnknownTypeWithoutLegacyFallback() {
    final String json =
        """
        {
          "id": "invalid",
          "type": "UNKNOWN",
          "stage": "TOOL"
        }
        """;

    assertThrows(RuntimeException.class, () -> JsonUtils.fromJson(json, GuardrailRule.class));
  }

  @Test
  void agentConfigRulesRoundTripPreservesSubtype() {
    final AgentConfig agentConfig = new AgentConfig();
    final GuardrailsConfig guardrails = new GuardrailsConfig();
    final ToolSafetyGuardrailRule toolRule = new ToolSafetyGuardrailRule();
    toolRule.setId("tool-rule");
    toolRule.setToolNames(List.of("shell"));
    guardrails.setRules(List.of(toolRule));
    agentConfig.setGuardrails(guardrails);

    final String json = JsonUtils.toJson(agentConfig);
    final AgentConfig parsed = JsonUtils.fromJson(json, AgentConfig.class);

    assertThat(parsed.getGuardrails().getRules()).hasSize(1);
    assertThat(parsed.getGuardrails().getRules().getFirst())
        .isInstanceOf(ToolSafetyGuardrailRule.class);
  }
}
