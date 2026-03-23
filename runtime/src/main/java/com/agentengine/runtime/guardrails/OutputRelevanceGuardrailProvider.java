package com.agentengine.runtime.guardrails;

import com.agentengine.util.agents.beans.config.GuardrailRuleType;
import com.agentengine.util.agents.beans.config.OutputRelevanceGuardrailRule;
import com.agentengine.runtime.guardrails.OutputRelevanceGuardrail;
import com.agentengine.runtime.factories.model.ModelProvider;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.mongodb.infra.DefaultModelConfig;
import com.agentengine.util.mongodb.infra.InfraMongoRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public final class OutputRelevanceGuardrailProvider implements GuardrailProvider<OutputRelevanceGuardrailRule> {
  private final ModelProvider modelProvider;
  private final InfraMongoRepository infraMongoRepository;

  @Inject
  public OutputRelevanceGuardrailProvider(final ModelProvider modelProvider, final InfraMongoRepository infraMongoRepository) {
    this.modelProvider = modelProvider;
    this.infraMongoRepository = infraMongoRepository;
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
    return new OutputRelevanceGuardrail(relevanceRule, new RelevanceScorer(modelProvider, resolveModel(relevanceRule)));
  }

  private String resolveModel(final OutputRelevanceGuardrailRule rule) {
    final String evaluatorModelId = rule.getEvaluatorModelId();
    return StringUtils.isNotBlank(evaluatorModelId) ? evaluatorModelId : resolveDefaultModelId();
  }

  private String resolveDefaultModelId() {
    try {
      final DefaultModelConfig defaults = infraMongoRepository.findOneByType(DefaultModelConfig.TYPE);
      return defaults == null ? null : defaults.getEvaluatorModelId();
    } catch (Exception ex) {
      return null;
    }
  }
}
