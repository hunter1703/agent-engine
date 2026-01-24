package com.agentengine.engine.builders.agent;

import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.ConfigRepository;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.MongoStateStoreConfig;
import com.agentengine.engine.api.beans.config.StateStoreConfig;
import com.agentengine.engine.api.builders.AgentBuilder;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.state.InMemoryStateStore;
import com.agentengine.engine.api.StateStore;

import java.util.logging.Logger;

public abstract class AbstractAgentBuilder<C extends AgentConfig, A extends Agent> implements AgentBuilder<C, A> {
  protected static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant.";

  protected final ModelProvider modelProvider;

  protected AbstractAgentBuilder(ModelProvider modelProvider) {
      this.modelProvider = modelProvider;
  }

}
