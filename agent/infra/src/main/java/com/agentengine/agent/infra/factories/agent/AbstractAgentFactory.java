package com.agentengine.agent.infra.factories.agent;

import com.agentengine.agent.infra.agents.Agent;
import com.agentengine.agent.infra.factories.agent.builders.BaseLlmAgentBuilder;
import com.agentengine.agent.infra.tools.ToolFactory;
import com.agentengine.agent.infra.utils.PromptUtils;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.RefCounted;
import com.agentengine.util.models.factories.Model;
import com.agentengine.util.models.factories.ModelProvider;
import com.agentengine.util.models.llm.AbstractLLM;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.BaseTool;
import com.google.genai.types.GenerateContentConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class AbstractAgentFactory<C extends BaseAgentConfig, A extends Agent>
    implements AgentFactory<C, A> {
  protected final ModelProvider modelProvider;
  protected final ToolFactory toolFactory;

  protected AbstractAgentFactory(final ModelProvider modelProvider, final ToolFactory toolFactory) {
    this.modelProvider = modelProvider;
    this.toolFactory = toolFactory;
  }

  protected BaseLlmAgentBuilder createLlmAgentBuilder(final BaseAgentConfig config) {
    final String modelId = config.getModelId();
    final RefCounted<Model.LLMModel> refCounted = modelProvider.get(modelId);
    if (!(refCounted.value().model() instanceof AbstractLLM)) {
      refCounted.close();
      throw new IllegalStateException("Model factory did not return an AbstractLLM instance.");
    }

    final LlmAgent.Builder builder = LlmAgent.builder();
    builder
        .disallowTransferToParent(false)
        .disallowTransferToPeers(false)
        .maxSteps(config.getRuntime().getMaxSteps())
        .model(refCounted.value().model());
    final Map<String, Object> responseFormat = config.getResponseFormat();
    if (CollectionUtils.isNotEmpty(responseFormat)) {
      builder.generateContentConfig(
          GenerateContentConfig.builder()
              .responseMimeType("application/json")
              .responseJsonSchema(responseFormat)
              .build());
    }
    final List<BaseTool> tools = new ArrayList<>(toolFactory.buildTools(config.getTools()));
    if (config.getRuntime().isResumable()) {
      tools.add(toolFactory.getHITLTool());
    }
    final BaseLlmAgentBuilder baseLlmAgentBuilder = new BaseLlmAgentBuilder(builder);
    return baseLlmAgentBuilder
        .systemInstructions(
            PromptUtils.renderSystemPrompt(
                config.getSystemPrompt(), config.getName(), config.getResponseFormat()))
        .appendTools(tools)
        .appendToolSets(toolFactory.buildToolsets(config.getTools()))
        .agentConfig(config)
        .closeHook(refCounted::close);
  }
}
