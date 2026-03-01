package com.agentengine.engine.builders.agent;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.builders.AgentBuilder;
import com.agentengine.engine.builders.context.ContextManagerProvider;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.builders.state.SessionServiceProvider;
import com.agentengine.engine.tools.ToolRegistry;
import com.google.adk.agents.LlmAgent;
import com.google.adk.sessions.BaseSessionService;

public abstract class AbstractAgentBuilder<C extends AgentConfig, A extends LlmAgent>
    implements AgentBuilder<C, A> {
  protected final ModelProvider modelProvider;
  protected final SessionServiceProvider sessionServiceProvider;
  protected final ContextManagerProvider contextManagerProvider;
  protected final ToolRegistry toolRegistry;

  protected AbstractAgentBuilder(
      ModelProvider modelProvider,
      SessionServiceProvider sessionServiceProvider,
      ContextManagerProvider contextManagerProvider,
      ToolRegistry toolRegistry) {
    this.modelProvider = modelProvider;
    this.sessionServiceProvider = sessionServiceProvider;
    this.contextManagerProvider = contextManagerProvider;
    this.toolRegistry = toolRegistry;
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
