package com.agentengine.engine.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.config.OutputRelevanceGuardrailRule;
import com.agentengine.engine.api.beans.config.TextContentGuardrailRule;
import com.agentengine.engine.api.beans.config.ToolSafetyGuardrailRule;
import com.agentengine.engine.guardrails.rules.OutputRelevanceGuardrail;
import com.agentengine.engine.guardrails.rules.TextContentGuardrail;
import com.agentengine.engine.guardrails.rules.ToolSafetyGuardrail;
import org.junit.jupiter.api.Test;

class GuardrailIdResolutionTest {

  @Test
  void usesConfiguredRuleIdWhenProvided() {
    final TextContentGuardrailRule rule = new TextContentGuardrailRule();
    rule.setId("input-policy-1");

    final TextContentGuardrail guardrail = new TextContentGuardrail(rule);

    assertThat(guardrail.id()).isEqualTo("input-policy-1");
  }

  @Test
  void generatesDistinctFallbackIdsForDifferentRuleInstances() {
    final TextContentGuardrail first = new TextContentGuardrail(new TextContentGuardrailRule());
    final TextContentGuardrail second = new TextContentGuardrail(new TextContentGuardrailRule());

    assertThat(first.id()).startsWith("text_content_");
    assertThat(second.id()).startsWith("text_content_");
    assertThat(first.id()).isNotEqualTo(second.id());
  }

  @Test
  void appliesSameRuleIdPatternToToolAndOutputRelevanceGuardrails() {
    final ToolSafetyGuardrailRule toolRule = new ToolSafetyGuardrailRule();
    toolRule.setId("tool-policy-1");
    final ToolSafetyGuardrail toolGuardrail = new ToolSafetyGuardrail(toolRule);

    final OutputRelevanceGuardrailRule relevanceRule = new OutputRelevanceGuardrailRule();
    relevanceRule.setId("relevance-policy-1");
    final OutputRelevanceGuardrail relevanceGuardrail =
        new OutputRelevanceGuardrail(relevanceRule, null);

    assertThat(toolGuardrail.id()).isEqualTo("tool-policy-1");
    assertThat(relevanceGuardrail.id()).isEqualTo("relevance-policy-1");
  }
}
