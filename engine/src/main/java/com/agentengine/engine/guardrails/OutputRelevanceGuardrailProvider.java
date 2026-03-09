package com.agentengine.engine.guardrails;

import com.agentengine.engine.api.beans.config.GuardrailRuleType;
import com.agentengine.engine.api.beans.config.OutputRelevanceGuardrailRule;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.guardrails.rules.OutputRelevanceGuardrail;
import com.agentengine.engine.infra.DefaultModelConfig;
import com.agentengine.engine.repository.InfraMongoRepository;
import com.google.adk.models.BaseLlm;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public final class OutputRelevanceGuardrailProvider
    implements GuardrailProvider<OutputRelevanceGuardrailRule> {
  private final ModelProvider modelProvider;
  private final InfraMongoRepository infraMongoRepository;

  @Inject
  public OutputRelevanceGuardrailProvider(
      final ModelProvider modelProvider, final InfraMongoRepository infraMongoRepository) {
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
    return new OutputRelevanceGuardrail(relevanceRule, new RelevanceEvaluator(modelProvider, resolveModel(relevanceRule)));
  }

  private String resolveModel(final OutputRelevanceGuardrailRule rule ) {
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
