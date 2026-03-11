package com.agentengine.engine.builders.agent;

import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.builders.AgentBuilder;
import com.agentengine.engine.api.services.SessionService;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.model.AbstractLLM;
import com.agentengine.engine.repository.ModelRepository;
import com.agentengine.engine.tools.ToolRegistry;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.tools.BaseTool;
import jakarta.enterprise.inject.Instance;
import java.util.List;

public abstract class AbstractAgentBuilder<C extends BaseAgentConfig, A extends Agent>
    implements AgentBuilder<C, A> {
  protected final ModelProvider modelProvider;
  protected final SessionService sessionService;
  protected final ToolRegistry toolRegistry;
  private final ModelRepository modelRepository;
  protected final Instance<AgentProvider> agentProvider;

  protected AbstractAgentBuilder(
      final ModelProvider modelProvider,
      final SessionService sessionService,
      final ToolRegistry toolRegistry,
      final ModelRepository modelRepository,
      final Instance<AgentProvider> agentProvider) {
    this.modelProvider = modelProvider;
    this.sessionService = sessionService;
    this.toolRegistry = toolRegistry;
    this.modelRepository = modelRepository;
    this.agentProvider = agentProvider;
  }

  protected DefaultLLMAgentBuilder getBuilder(final BaseAgentConfig config) {
    final ModelConfig modelConfig = modelRepository.findById(config.getModelId()).orElse(null);
    if (modelConfig == null) {
      throw new IllegalStateException("Model config not found for agent.");
    }

    final BaseLlm model = modelProvider.get(config.getModelId());
    if (!(model instanceof AbstractLLM agentModel)) {
      throw new IllegalStateException("Model builder did not return an AbstractLLM instance.");
    }

    final LlmAgent.Builder builder = LlmAgent.builder();
    builder
        .disallowTransferToParent(false)
        .disallowTransferToPeers(false)
        .maxSteps(resolveMaxSteps(config))
        .model(model);
    final DefaultLLMAgentBuilder defaultLLMAgentBuilder = new DefaultLLMAgentBuilder(builder);
    defaultLLMAgentBuilder.parseToolCallsFromText(agentModel.isParseToolCallsFromText());
    if (agentModel.isToolCallingEnabled()) {
      final List<BaseTool> tools =
          CollectionUtils.nullSafeMutableList(
              toolRegistry.loadTools(config.getId(), config.getTools()));
      if (CollectionUtils.isNotEmpty(tools)) {
        defaultLLMAgentBuilder.appendTools(tools);
      }
    }
    return defaultLLMAgentBuilder
        .agentConfig(config)
        .protocolInstructions(agentModel.getProtocol())
        .systemInstructions(config.getSystemPrompt());
  }

  private static int resolveMaxSteps(final BaseAgentConfig config) {
    if (config == null || config.getRuntime() == null || config.getRuntime().getMaxSteps() <= 0) {
      return 50;
    }
    return config.getRuntime().getMaxSteps();
  }
}
