package com.agentengine.engine.builders.agent;

import com.agentengine.engine.agents.story.StoryAgent;
import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.builders.context.ContextManagerProvider;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.builders.state.SessionServiceProvider;
import com.agentengine.engine.guardrails.GuardrailRegistry;
import com.agentengine.engine.model.AbstractLLM;
import com.agentengine.engine.repository.ModelRepository;
import com.agentengine.engine.tools.ToolRegistry;
import com.google.adk.models.BaseLlm;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StoryAgentBuilder is responsible for creating instances of {@link StoryAgent}.
 * It extends {@link AbstractAgentBuilder} and returns a StoryAgent.
 */
@Singleton
public class StoryAgentBuilder extends AbstractAgentBuilder<AgentConfig, StoryAgent> {
  private static final Logger LOG = LoggerFactory.getLogger(StoryAgentBuilder.class);

  @Inject
  public StoryAgentBuilder(
      final ModelProvider modelProvider,
      final SessionServiceProvider sessionServiceProvider,
      final ToolRegistry toolRegistry,
      final ContextManagerProvider contextManagerProvider,
      final ModelRepository modelRepository,
      final GuardrailRegistry guardrailRegistry) {
    super(
        modelProvider,
        sessionServiceProvider,
        contextManagerProvider,
        toolRegistry,
        modelRepository,
        guardrailRegistry);
  }

  @Override
  public StoryAgent build(final AgentConfig config, final AgentContext agentContext) {
    final LLMStoryAgentBuilder builder = (LLMStoryAgentBuilder) getBuilder(config, agentContext);
    builder.withRoutingHistorySize(config.getRoutingHistorySize()).withRoutingModel(resolveRoutingModel(config));
    return new StoryAgent(builder);
  }

  @Override
  protected LLMAgentBuilder getEmptyBuilder() {
    return new LLMStoryAgentBuilder();
  }

  @Override
  public String type() {
    return AgentConfig.AgentType.STORY.name().toLowerCase();
  }

  private AbstractLLM resolveRoutingModel(final AgentConfig config) {
    final String routingModelId = config.getRoutingModelId();
    if (StringUtils.isNotBlank(routingModelId)) {
      try {
        final AgentModelConfig routingConfig = new AgentModelConfig(routingModelId);
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
}
