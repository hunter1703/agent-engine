package com.agentengine.engine.guardrails;

import com.agentengine.engine.api.beans.config.GuardrailRuleType;
import com.agentengine.engine.api.beans.config.GuardrailsConfig;
import com.agentengine.engine.api.beans.config.OutputRelevanceGuardrailRule;
import com.agentengine.engine.api.beans.config.AgentModelConfig;
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
  public Guardrail create(
      final GuardrailsConfig guardrailsConfig,
      final OutputRelevanceGuardrailRule relevanceRule) {
    if (relevanceRule == null || !relevanceRule.isEnabled()) {
      return null;
    }
    return new OutputRelevanceGuardrail(relevanceRule, new RelevanceEvaluator(resolveModel(relevanceRule.getEvaluatorModelId())));
  }

  private BaseLlm resolveModel(final String explicitModelId) {
    final String modelId =
        StringUtils.isNotBlank(explicitModelId) ? explicitModelId : resolveDefaultModelId();
    if (StringUtils.isBlank(modelId)) {
      return null;
    }
    final AgentModelConfig config = new AgentModelConfig();
    config.setModelId(modelId);
    try {
      return modelProvider.get(config);
    } catch (Exception ex) {
      return null;
    }
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
