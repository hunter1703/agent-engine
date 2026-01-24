package com.agentengine.engine.builders.agent;

import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.ConfigRepository;
import com.agentengine.engine.HybridAgent;
import com.agentengine.engine.api.beans.config.*;
import com.agentengine.engine.agents.PlanningAgent;
import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.context.LastNContextManager;
import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.api.StateStore;
import com.agentengine.engine.tools.Tool;
import com.agentengine.engine.tools.DefaultToolExecutor;
import com.agentengine.engine.tools.ToolExecutor;
import com.agentengine.engine.tools.ToolRegistry;
import com.agentengine.engine.tools.UserClarificationTool;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.ResourceUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.api.utils.TemplateUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public final class HybridAgentBuilder extends AbstractAgentBuilder<HybridAgentConfig, HybridAgent> {

    private final PlanningAgentBuilder planningAgentBuilder;

    public HybridAgentBuilder(final ModelProvider modelProvider, PlanningAgentBuilder planningAgentBuilder) {
        super(modelProvider);
        this.planningAgentBuilder = planningAgentBuilder;
    }

    public HybridAgent build(final HybridAgentConfig agentConfig) {
        final LLMModel reasoningModel = modelProvider.get(agentConfig.getName(), agentConfig.getModel());
        final LLMModel routerModel = modelProvider.get(agentConfig.getName(), agentConfig.getRouterModel());
        final LLMModel planningModel = modelProvider.get(agentConfig.getName(), agentConfig.getPlanningModel());

        final ToolExecutor toolExecutor = new DefaultToolExecutor(ToolRegistry.loadTools(agentConfig.getName(), agentConfig.getModel().getTools()));

        final PlanningAgent planningAgent = new PlanningAgent(planningModel);

        return new HybridAgent(routerModel, planningAgent, reasoningModel, toolExecutor);
    }

    @Override
    public String type() {
        return "hybrid";
    }
}
