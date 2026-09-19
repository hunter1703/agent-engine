package com.agentengine.agent.infra.guardrails;

import com.agentengine.util.agents.beans.config.GuardrailRuleType;
import com.agentengine.util.agents.beans.config.OutputRelevanceGuardrailRule;
import com.agentengine.util.agents.repository.DefaultModelsRepository;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.models.factories.ModelProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public final class OutputRelevanceGuardrailFactory
    implements GuardrailFactory<OutputRelevanceGuardrailRule> {
  private final ModelProvider modelProvider;
  private final DefaultModelsRepository defaultModelsRepository;

  @Inject
  public OutputRelevanceGuardrailFactory(
      final ModelProvider modelProvider, final DefaultModelsRepository defaultModelsRepository) {
    this.modelProvider = modelProvider;
    this.defaultModelsRepository = defaultModelsRepository;
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
    return StringUtils.isNotBlank(evaluatorModelId) ? evaluatorModelId : resolveDefaultModelId();
  }

  private String resolveDefaultModelId() {
    try {
      return defaultModelsRepository.getEvaluatorModelId();
    } catch (Exception ex) {
      return null;
    }
  }
}
