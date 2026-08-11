package com.agentengine.agent.infra.guardrails;

import com.agentengine.agent.infra.factories.model.ModelProvider;
import com.agentengine.util.agents.beans.config.GuardrailRuleType;
import com.agentengine.util.agents.beans.config.OutputRelevanceGuardrailRule;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.mongodb.infra.DefaultModelsConfig;
import com.agentengine.util.mongodb.infra.InfraConfigService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public final class OutputRelevanceGuardrailFactory implements GuardrailFactory<OutputRelevanceGuardrailRule> {
    private final ModelProvider modelProvider;
    private final InfraConfigService infraConfigService;

    @Inject
    public OutputRelevanceGuardrailFactory(
            final ModelProvider modelProvider, final InfraConfigService infraConfigService) {
        this.modelProvider = modelProvider;
        this.infraConfigService = infraConfigService;
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
            final DefaultModelsConfig defaults = infraConfigService.findById(
                    DefaultModelsConfig.CATEGORY, DefaultModelsConfig.TYPE, DefaultModelsConfig.CONFIG_ID);
            return defaults == null ? null : defaults.getEvaluatorModelId();
        } catch (Exception ex) {
            return null;
        }
    }
}
