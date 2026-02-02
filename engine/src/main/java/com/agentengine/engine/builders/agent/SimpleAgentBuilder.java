package com.agentengine.engine.builders.agent;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.builders.context.ContextManagerProvider;
import com.agentengine.engine.model.LangChain4JLLMModel;
import com.agentengine.engine.agents.SimpleAgent;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.builders.state.SessionServiceProvider;
import com.agentengine.engine.tools.ToolRegistry;
import com.agentengine.engine.tools.ToolUtils;
import com.google.adk.tools.BaseTool;
import com.google.adk.sessions.BaseSessionService;
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
  public SimpleAgent build(final AgentConfig config, final AgentContext agentContext) {
    final AgentBuilder builder = getBuilder(config, agentContext);
    return new SimpleAgent(builder);
  }

  protected AgentBuilder getBuilder(final AgentConfig config, final AgentContext agentContext) {
    final LangChain4JLLMModel model = modelProvider.get(config.getAgentId(), config.getModel());
    final BaseSessionService sessionService = resolveSessionService(agentContext, config);
    final AgentContext resolvedContext = sessionService == null
        ? agentContext
        : new AgentContext(config, sessionService);
    final boolean toolCallingEnabled = model.isToolCallingEnabled();
    final boolean parseToolCallsFromText = model.isParseToolCallsFromText();
    String toolInstructions = "";
    final AgentBuilder agentBuilder = new AgentBuilder();
    if (toolCallingEnabled) {
      final List<BaseTool> tools = toolRegistry.loadTools(resolvedContext, config.getModel().getTools());
      if (parseToolCallsFromText) {
        toolInstructions = ToolUtils.buildToolMessage(tools);
      }
      if (CollectionUtils.isNotEmpty(tools)) {
        agentBuilder.tools(tools);
      }
    }
    agentBuilder.toolInstructions(toolInstructions).protocolInstructions(model.getProtocol())
        .globalInstruction(config.getModel().getSystemPrompt()).disallowTransferToParent(false)
        .disallowTransferToPeers(false).name(config.getAgentId()).model(model);
    return agentBuilder;
  }

  private BaseSessionService resolveSessionService(final AgentContext agentContext, final AgentConfig config) {
    if (agentContext != null && agentContext.sessionService() != null) {
      return agentContext.sessionService();
    }
    if (config == null) {
      return null;
    }
    return sessionServiceProvider.get(config.getSessionStore());
  }

  @Override
  public String type() {
    return AgentConfig.AgentType.DEFAULT.name().toLowerCase();
  }

}
