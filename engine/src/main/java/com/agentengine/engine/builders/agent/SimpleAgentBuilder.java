package com.agentengine.engine.builders.agent;

import com.agentengine.engine.agents.SimpleAgent;
import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.builders.context.ContextManagerProvider;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.builders.state.SessionServiceProvider;
import com.agentengine.engine.model.AbstractLLM;
import com.agentengine.engine.repository.ModelRepository;
import com.agentengine.engine.tools.ToolRegistry;
import com.agentengine.engine.tools.ToolUtils;
import com.google.adk.models.BaseLlm;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.tools.BaseTool;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
@Named("simpleAgentBuilder")
public class SimpleAgentBuilder extends AbstractAgentBuilder<AgentConfig, SimpleAgent> {

  private final ModelRepository modelRepository;

  @Inject
  public SimpleAgentBuilder(
      ModelProvider modelProvider,
      SessionServiceProvider sessionServiceProvider,
      ToolRegistry toolRegistry,
      ContextManagerProvider contextManagerProvider,
      ModelRepository modelRepository) {
    super(modelProvider, sessionServiceProvider, contextManagerProvider, toolRegistry);
    this.modelRepository = modelRepository;
  }

  @Override
  public SimpleAgent build(final AgentConfig config, final AgentContext agentContext) {
    final AgentBuilder builder = getBuilder(config, agentContext);
    return new SimpleAgent(builder);
  }

  protected AgentBuilder getBuilder(final AgentConfig config, final AgentContext agentContext) {
    final ModelConfig modelConfig =
        modelRepository.findById(config.getModel().getModelId()).orElse(null);
    if (modelConfig == null) {
      throw new IllegalStateException("Model config not found for agent.");
    }
    final BaseLlm model = modelProvider.get(config.getModel());
    if (!(model instanceof AbstractLLM agentModel)) {
      throw new IllegalStateException("Model builder did not return an AbstractLLM instance.");
    }
    final BaseSessionService sessionService = resolveSessionService(agentContext, config);
    final AgentContext resolvedContext =
        sessionService == null ? agentContext : new AgentContext(config, sessionService);
    final boolean toolCallingEnabled = agentModel.isToolCallingEnabled();
    final boolean parseToolCallsFromText = agentModel.isParseToolCallsFromText();
    String toolInstructions = "";
    final AgentBuilder agentBuilder = new AgentBuilder();
    if (toolCallingEnabled) {
      final List<BaseTool> tools =
          toolRegistry.loadTools(resolvedContext, config.getModel().getTools());
      if (parseToolCallsFromText) {
        toolInstructions = ToolUtils.buildToolMessage(tools);
      }
      if (CollectionUtils.isNotEmpty(tools)) {
        agentBuilder.tools(tools);
      }
    }
    final String globalInstruction =
        buildGlobalInstruction(config.getModel().getSystemPrompt(), modelConfig.getInstructions());
    final String agentName = resolveAgentName(config);
    agentBuilder
        .toolInstructions(toolInstructions)
        .protocolInstructions(agentModel.getProtocol())
        .globalInstruction(globalInstruction)
        .disallowTransferToParent(false)
        .disallowTransferToPeers(false)
        .name(agentName)
        .model(model);
    return agentBuilder;
  }

  private static String resolveAgentName(final AgentConfig config) {
    if (config == null) {
      return "agent";
    }
    final String candidate =
        StringUtils.isBlank(config.getName()) ? config.getId() : config.getName();
    return StringUtils.isBlank(candidate) ? "agent" : candidate;
  }

  private static String buildGlobalInstruction(
      final String systemPrompt, final String modelInstructions) {
    if (StringUtils.isBlank(modelInstructions)) {
      return systemPrompt;
    }
    if (StringUtils.isBlank(systemPrompt)) {
      return modelInstructions;
    }
    return STR."\{systemPrompt}\n\n# FOLLOW\n\{modelInstructions}";
  }

  private BaseSessionService resolveSessionService(
      final AgentContext agentContext, final AgentConfig config) {
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
