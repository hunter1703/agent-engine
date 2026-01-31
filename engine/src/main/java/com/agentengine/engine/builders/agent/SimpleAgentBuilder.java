package com.agentengine.engine.builders.agent;

import com.agentengine.engine.builders.context.ContextManagerProvider;
import com.agentengine.engine.model.LangChain4JLLMModel;
import com.agentengine.engine.agents.SimpleAgent;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.builders.state.SessionServiceProvider;
import com.agentengine.engine.tools.ToolRegistry;
import com.agentengine.engine.tools.ToolUtils;
import com.google.adk.tools.BaseTool;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
@Named("simpleAgentBuilder")
public class SimpleAgentBuilder extends AbstractAgentBuilder<AgentConfig, SimpleAgent> {

    @Inject
    public SimpleAgentBuilder(ModelProvider modelProvider, SessionServiceProvider sessionServiceProvider,
                              ToolRegistry toolRegistry, ContextManagerProvider contextManagerProvider) {
        super(modelProvider, sessionServiceProvider, contextManagerProvider, toolRegistry);
    }

    @Override
    public SimpleAgent build(AgentConfig config) {
        final AgentBuilder builder = getBuilder(config);
        return new SimpleAgent(builder);
    }

    protected AgentBuilder getBuilder(final AgentConfig config) {
        final LangChain4JLLMModel model = modelProvider.get(config.getAgentId(), config.getModel());
        final List<BaseTool> tools = toolRegistry.loadTools(config.getAgentId(), config.getModel().getTools());
        return (AgentBuilder) new AgentBuilder().model(model).protocolInstructions(model.getProtocol()).toolInstructions(ToolUtils.buildToolMessage(tools)).globalInstruction(config.getModel().getSystemPrompt()).planning(true);
    }

    @Override
    public String type() {
        return AgentConfig.AgentType.DEFAULT.name().toLowerCase();
    }

}