package com.agentengine.agent.infra.factories.context;

import com.agentengine.agent.infra.context.CompactionContextManager;
import com.agentengine.agent.infra.context.ContextManager;
import com.agentengine.agent.infra.factories.model.ModelProvider;
import com.agentengine.catalog.api.services.SessionService;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.config.CompactionContextStrategyConfig;
import com.agentengine.util.agents.beans.config.ContextStrategyConfig;
import com.agentengine.util.agents.beans.config.DefaultAgentConfig;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.mongodb.infra.DefaultModelsConfig;
import com.agentengine.util.mongodb.infra.InfraConfigService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class CompactionContextManagerFactory
        implements ContextManagerFactory<CompactionContextStrategyConfig, ContextManager> {

    private final ModelProvider modelProvider;
    private final SessionService sessionService;
    private final InfraConfigService infraConfigService;

    @Inject
    public CompactionContextManagerFactory(
            final ModelProvider modelProvider,
            final SessionService sessionService,
            final InfraConfigService infraConfigService) {
        this.modelProvider = modelProvider;
        this.sessionService = sessionService;
        this.infraConfigService = infraConfigService;
    }

    @Override
    public ContextManager build(final CompactionContextStrategyConfig config, final BaseAgentConfig agentConfig) {
        return new CompactionContextManager(
                config.getTokenThreshold(),
                config.getRecencyThreshold(),
                resolveModelId(config, agentConfig),
                config.getPromptTemplate(),
                modelProvider,
                sessionService);
    }

    @Override
    public ContextManager build(final CompactionContextStrategyConfig config) {
        return build(config, new DefaultAgentConfig());
    }

    private String resolveModelId(final CompactionContextStrategyConfig config, final BaseAgentConfig agentConfig) {
        if (StringUtils.isNotBlank(config.getModelId())) {
            return config.getModelId();
        }
        final DefaultModelsConfig defaults = infraConfigService.findById(
                DefaultModelsConfig.CATEGORY, DefaultModelsConfig.TYPE, DefaultModelsConfig.CONFIG_ID);
        if (defaults != null && StringUtils.isNotBlank(defaults.getCompactionModelId())) {
            return defaults.getCompactionModelId();
        }
        return agentConfig.getModelId();
    }

    @Override
    public String type() {
        return ContextStrategyConfig.ContextStrategyType.COMPACTION.type();
    }
}
