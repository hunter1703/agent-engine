package com.agentengine.engine.builders.agent;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.GuardrailErrorMode;
import com.agentengine.engine.api.beans.config.GuardrailStage;
import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.beans.config.OutputEvaluationConfig;
import com.agentengine.engine.api.builders.AgentBuilder;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.builders.context.ContextManagerProvider;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.builders.state.SessionServiceProvider;
import com.agentengine.engine.guardrails.GuardrailRegistry;
import com.agentengine.engine.infra.DefaultModelConfig;
import com.agentengine.engine.repository.InfraMongoRepository;
import com.agentengine.engine.model.AbstractLLM;
import com.agentengine.engine.repository.ModelRepository;
import com.agentengine.engine.tools.ToolRegistry;
import com.agentengine.engine.tools.ToolUtils;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.tools.BaseTool;
import com.google.genai.types.Content;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractAgentBuilder<C extends AgentConfig, A extends LlmAgent>
    implements AgentBuilder<C, A> {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractAgentBuilder.class);
  protected final ModelProvider modelProvider;
  protected final SessionServiceProvider sessionServiceProvider;
  protected final ContextManagerProvider contextManagerProvider;
  protected final ToolRegistry toolRegistry;
  protected final GuardrailRegistry guardrailRegistry;
  protected final InfraMongoRepository infraMongoRepository;
  private final ModelRepository modelRepository;

  protected AbstractAgentBuilder(
          ModelProvider modelProvider,
          SessionServiceProvider sessionServiceProvider,
          ContextManagerProvider contextManagerProvider,
          ToolRegistry toolRegistry,
          ModelRepository modelRepository,
          GuardrailRegistry guardrailRegistry,
          InfraMongoRepository infraMongoRepository) {
    this.modelProvider = modelProvider;
    this.sessionServiceProvider = sessionServiceProvider;
    this.contextManagerProvider = contextManagerProvider;
    this.toolRegistry = toolRegistry;
    this.modelRepository = modelRepository;
    this.guardrailRegistry = guardrailRegistry;
    this.infraMongoRepository = infraMongoRepository;
  }

  protected LLMAgentBuilder getBuilder(final AgentConfig config, final AgentContext agentContext) {
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
    final ContextManager contextManager = contextManagerProvider.get(config.getModel().getContextManagerConfig(), config);
    String toolInstructions = "";
    final LLMAgentBuilder llmAgentBuilder = getEmptyBuilder();
    if (toolCallingEnabled) {
      final List<BaseTool> tools =
              CollectionUtils.nullSafeMutableList(toolRegistry.loadTools(resolvedContext, config.getModel().getTools()));
      if (parseToolCallsFromText) {
        toolInstructions = ToolUtils.buildToolMessage(tools);
      }
      if (CollectionUtils.isNotEmpty(tools)) {
        llmAgentBuilder.tools(tools);
      }
    }
    final String globalInstruction =
            buildGlobalInstruction(config.getModel().getSystemPrompt(), modelConfig.getInstructions());
    llmAgentBuilder
            .toolInstructions(toolInstructions)
            .protocolInstructions(agentModel.getProtocol())
            .globalInstruction(globalInstruction)
            .outputEvaluationConfig(hydrateOutputEvaluationConfig(config.getOutputEvaluation()))
            .outputGuardrails(
                guardrailRegistry.getGuardrailsForStage(
                    config.getGuardrails(), GuardrailStage.OUTPUT))
            .guardrailErrorMode(
                config.getGuardrails() == null
                    ? GuardrailErrorMode.FAIL_OPEN
                    : config.getGuardrails().getDefaultOnError())
            .disallowTransferToParent(false)
            .disallowTransferToPeers(false)
            .name(config.getId())
            .model(model)
            .beforeModelCallbackSync(
                (callbackContext, llmRequestBuilder) -> {
                  try {
                    final List<Content> contents =
                        CollectionUtils.nullSafeList(llmRequestBuilder.build().contents());
                    final List<Content> prompt =
                        contextManager.buildPrompt(config.getId(), callbackContext.sessionId(), contents);
                    llmRequestBuilder.contents(prompt);
                  } catch (final Exception ex) {
                    LOG.warn(
                        "Context manager failed for agent_id={} session_id={}; continuing with unmodified prompt.",
                        config.getId(),
                        callbackContext.sessionId(),
                        ex);
                  }
                  return Optional.empty();
                });
    return llmAgentBuilder;
  }

  protected LLMAgentBuilder getEmptyBuilder() {
    return new LLMAgentBuilder();
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

  private OutputEvaluationConfig hydrateOutputEvaluationConfig(final OutputEvaluationConfig outputEvaluationConfig) {
    if (outputEvaluationConfig == null) {
      return null;
    }
    String evaluatorModelId = outputEvaluationConfig.getEvaluatorModelId();
    if (StringUtils.isBlank(evaluatorModelId)) {
      final DefaultModelConfig defaultModelConfig = infraMongoRepository.findOneByType(DefaultModelConfig.TYPE);
        if (defaultModelConfig != null) {
            evaluatorModelId = defaultModelConfig.getEvaluatorModelId();
        }
    }
    if (StringUtils.isBlank(evaluatorModelId)) {
      return outputEvaluationConfig;
    }
    outputEvaluationConfig.setEvaluatorModel(modelProvider.get(new AgentModelConfig(evaluatorModelId)));
    return outputEvaluationConfig;
  }
}
