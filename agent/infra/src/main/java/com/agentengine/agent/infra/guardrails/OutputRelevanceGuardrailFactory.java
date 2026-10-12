package com.agentengine.agent.infra.guardrails;

import com.agentengine.util.agents.beans.config.GuardrailRuleType;
import com.agentengine.util.agents.beans.config.OutputRelevanceGuardrailRule;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.models.config.DefaultModels;
import com.agentengine.util.models.factories.ModelProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public final class OutputRelevanceGuardrailFactory
    implements GuardrailFactory<OutputRelevanceGuardrailRule> {
  private final ModelProvider modelProvider;

  @Inject
  public OutputRelevanceGuardrailFactory(final ModelProvider modelProvider) {
    this.modelProvider = modelProvider;
  }

  @Override
  public GuardrailRuleType type() {
    return GuardrailRuleType.RELEVANCE;
  }

  @Override
  public Guardrail create(final OutputRelevanceGuardrailRule relevanceRule) {
    if (relevanceRule == null || !relevanceRule.isEnabled()) {
      return null;
    }
    return new OutputRelevanceGuardrail(
        relevanceRule, new RelevanceScorer(modelProvider, resolveModel(relevanceRule)));
  }

  private String resolveModel(final OutputRelevanceGuardrailRule rule) {
    final String evaluatorModelId = rule.getEvaluatorModelId();
    return StringUtils.isNotBlank(evaluatorModelId) ? evaluatorModelId : DefaultModels.FAST_ID;
  }
}
