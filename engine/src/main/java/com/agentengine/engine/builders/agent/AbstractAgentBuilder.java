package com.agentengine.engine.builders.agent;

import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.ToolsConfig;
import com.agentengine.engine.api.builders.AgentBuilder;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.builders.sessionstore.SessionStoreProvider;
import com.agentengine.engine.tools.DefaultToolExecutor;
import com.agentengine.engine.api.Tool;
import com.agentengine.engine.tools.ToolExecutor;
import com.agentengine.engine.tools.ToolRegistry;

import java.util.List;

public abstract class AbstractAgentBuilder<C extends AgentConfig, A extends Agent> implements AgentBuilder<C, A> {
  protected final ModelProvider modelProvider;
  protected final SessionStoreProvider sessionStoreProvider;
  protected final ToolRegistry toolRegistry;

  protected AbstractAgentBuilder(ModelProvider modelProvider, SessionStoreProvider sessionStoreProvider,
      ToolRegistry toolRegistry) {
    this.modelProvider = modelProvider;
    this.sessionStoreProvider = sessionStoreProvider;
    this.toolRegistry = toolRegistry;
  }

  protected ToolExecutor getDefaultToolExecutor(final String agentId, final ToolsConfig toolsConfig) {
    final List<Tool> tools = toolRegistry.loadTools(agentId, toolsConfig);
    return new DefaultToolExecutor(tools);
  }
}
