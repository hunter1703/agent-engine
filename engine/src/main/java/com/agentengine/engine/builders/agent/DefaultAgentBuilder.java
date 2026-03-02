package com.agentengine.engine.builders.agent;

import com.agentengine.engine.agents.DefaultAgent;
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
@Named("defaultAgentBuilder")
public class DefaultAgentBuilder extends AbstractAgentBuilder<AgentConfig, DefaultAgent> {

  @Inject
  public DefaultAgentBuilder(
      ModelProvider modelProvider,
      SessionServiceProvider sessionServiceProvider,
      ToolRegistry toolRegistry,
      ContextManagerProvider contextManagerProvider,
      ModelRepository modelRepository) {
    super(modelProvider, sessionServiceProvider, contextManagerProvider, toolRegistry, modelRepository);
  }

  @Override
  public DefaultAgent build(final AgentConfig config, final AgentContext agentContext) {
    final AgentBuilder builder = getBuilder(config, agentContext);
    return new DefaultAgent(builder);
  }

  @Override
  public String type() {
    return AgentConfig.AgentType.DEFAULT.name().toLowerCase();
  }
}
