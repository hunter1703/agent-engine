package com.agentengine.engine.builders.agent;

import com.agentengine.engine.agents.PlanningAgent;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.builders.context.ContextManagerProvider;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.builders.state.SessionServiceProvider;
import com.agentengine.engine.tools.ToolRegistry;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named("planningAgentBuilder")
public final class PlanningAgentBuilder extends SimpleAgentBuilder {

  public PlanningAgentBuilder(final ModelProvider modelProvider, SessionServiceProvider sessionServiceProvider,
                              ToolRegistry toolRegistry, ContextManagerProvider contextManagerProvider) {
    super(modelProvider, sessionServiceProvider, toolRegistry, contextManagerProvider);
  }

  @Override
  public PlanningAgent build(final AgentConfig agentConfig) {
    return new PlanningAgent(getBuilder(agentConfig));
  }

  @Override
  public String type() {
    return "planning";
  }
}
