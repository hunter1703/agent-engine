package com.agentengine.engine.builders.agent;

import com.agentengine.engine.agents.story.StoryAgent;
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
import com.agentengine.engine.tools.SubmitFinalAnswerTool;
import com.agentengine.engine.tools.ToolRegistry;
import com.agentengine.engine.tools.ToolUtils;
import com.google.adk.models.BaseLlm;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.tools.BaseTool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

/**
 * StoryAgentBuilder is responsible for creating instances of {@link StoryAgent}.
 * It extends {@link AbstractAgentBuilder} and returns a StoryAgent.
 */
@Singleton
public class StoryAgentBuilder extends AbstractAgentBuilder<AgentConfig, StoryAgent> {

  @Inject
  public StoryAgentBuilder(
      final ModelProvider modelProvider,
      final SessionServiceProvider sessionServiceProvider,
      final ToolRegistry toolRegistry,
      final ContextManagerProvider contextManagerProvider,
      final ModelRepository modelRepository) {
    super(modelProvider, sessionServiceProvider, contextManagerProvider, toolRegistry, modelRepository);
  }

  @Override
  public StoryAgent build(final AgentConfig config, final AgentContext agentContext) {
    final AgentBuilder builder = getBuilder(config, agentContext);
    return new StoryAgent(builder);
  }

  @Override
  public String type() {
    return AgentConfig.AgentType.STORY.name().toLowerCase();
  }
}
