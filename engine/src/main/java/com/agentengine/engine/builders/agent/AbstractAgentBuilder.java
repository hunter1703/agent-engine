package com.agentengine.engine.builders.agent;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.builders.AgentBuilder;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.builders.context.ContextManagerProvider;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.builders.state.SessionServiceProvider;
import com.agentengine.engine.model.AbstractLLM;
import com.agentengine.engine.repository.ModelRepository;
import com.agentengine.engine.tools.ToolRegistry;
import com.agentengine.engine.tools.ToolUtils;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.tools.BaseTool;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractAgentBuilder<C extends AgentConfig, A extends LlmAgent>
    implements AgentBuilder<C, A> {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractAgentBuilder.class);
  private static final Pattern ADK_AGENT_NAME_PATTERN =
      Pattern.compile("^_?[a-zA-Z0-9]*([. _-][a-zA-Z0-9]+)*$");
  protected final ModelProvider modelProvider;
  protected final SessionServiceProvider sessionServiceProvider;
  protected final ContextManagerProvider contextManagerProvider;
  protected final ToolRegistry toolRegistry;
  private final ModelRepository modelRepository;

  protected AbstractAgentBuilder(
          ModelProvider modelProvider,
          SessionServiceProvider sessionServiceProvider,
          ContextManagerProvider contextManagerProvider,
          ToolRegistry toolRegistry, ModelRepository modelRepository) {
    this.modelProvider = modelProvider;
    this.sessionServiceProvider = sessionServiceProvider;
    this.contextManagerProvider = contextManagerProvider;
    this.toolRegistry = toolRegistry;
      this.modelRepository = modelRepository;
  }

  protected com.agentengine.engine.builders.agent.AgentBuilder getBuilder(final AgentConfig config, final AgentContext agentContext) {
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
    final com.agentengine.engine.builders.agent.AgentBuilder agentBuilder = new com.agentengine.engine.builders.agent.AgentBuilder();
    if (toolCallingEnabled) {
      final List<BaseTool> tools =
              CollectionUtils.nullSafeMutableList(toolRegistry.loadTools(resolvedContext, config.getModel().getTools()));
      if (parseToolCallsFromText) {
        toolInstructions = ToolUtils.buildToolMessage(tools);
      }
      if (CollectionUtils.isNotEmpty(tools)) {
        agentBuilder.tools(tools);
      }
    }
    final String globalInstruction =
            buildGlobalInstruction(config.getModel().getSystemPrompt(), modelConfig.getInstructions());
    agentBuilder
            .toolInstructions(toolInstructions)
            .protocolInstructions(agentModel.getProtocol())
            .globalInstruction(globalInstruction)
            .disallowTransferToParent(false)
            .disallowTransferToPeers(false)
            .name(config.getId())
            .model(model);
    return agentBuilder;
  }

  private static String buildGlobalInstruction(
          final String systemPrompt, final String modelInstructions) {
    if (StringUtils.isBlank(modelInstructions)) {
      return systemPrompt;
    }
    if (StringUtils.isBlank(systemPrompt)) {
      return modelInstructions;
    }
    return systemPrompt + "\n\n# FOLLOW\n" + modelInstructions;
  }

  protected AbstractLLM resolveRoutingModel(final AgentConfig config) {
    final String routingModelId = config.getRoutingModelId();
    if (StringUtils.isNotBlank(routingModelId)) {
      try {
        final AgentModelConfig routingConfig = new AgentModelConfig();
        routingConfig.setModelId(routingModelId);
        final BaseLlm routingModel = modelProvider.get(routingConfig);
        if (routingModel instanceof AbstractLLM abstractLLM) {
          return abstractLLM;
        }
        LOG.warn("Routing model '{}' did not produce an AbstractLLM, falling back to main model", routingModelId);
      } catch (final Exception e) {
        LOG.warn("Failed to resolve routing model '{}', falling back to main model", routingModelId, e);
      }
    }
    final BaseLlm mainModel = modelProvider.get(config.getModel());
    if (mainModel instanceof AbstractLLM abstractLLM) {
      return abstractLLM;
    }
    throw new IllegalStateException("Main model is not an AbstractLLM instance");
  }

  protected BaseSessionService resolveSessionService(
      final AgentContext agentContext, final AgentConfig config) {
    if (agentContext != null && agentContext.sessionService() != null) {
      return agentContext.sessionService();
    }
    if (config == null) {
      return null;
    }
    return sessionServiceProvider.get(config.getSessionStore());
  }
}
