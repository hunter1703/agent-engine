package com.agentengine.engine.builders.agent;

import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.ConfigRepository;
import com.agentengine.engine.api.SessionStore;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.builders.AgentBuilder;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.builders.sessionstore.SessionStoreProvider;
import com.agentengine.engine.state.InMemorySessionStore;

import java.util.logging.Logger;

public abstract class AbstractAgentBuilder<C extends AgentConfig, A extends Agent> implements AgentBuilder<C, A> {
  protected final ModelProvider modelProvider;
  protected final SessionStoreProvider sessionStoreProvider;

  protected AbstractAgentBuilder(ModelProvider modelProvider, SessionStoreProvider sessionStoreProvider) {
      this.modelProvider = modelProvider;
      this.sessionStoreProvider = sessionStoreProvider;
  }

}
