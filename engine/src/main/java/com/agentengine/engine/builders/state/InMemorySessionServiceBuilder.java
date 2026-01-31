package com.agentengine.engine.builders.state;

import com.agentengine.engine.api.beans.config.InMemorySessionServiceConfig;
import com.agentengine.engine.api.builders.SessionServiceBuilder;
import com.google.adk.sessions.InMemorySessionService;
import jakarta.inject.Singleton;

import static com.agentengine.engine.api.beans.config.SessionServiceConfig.SessionServiceType.MEMORY;

@Singleton
public class InMemorySessionServiceBuilder implements SessionServiceBuilder<InMemorySessionServiceConfig, InMemorySessionService> {

  @Override
  public InMemorySessionService build(final InMemorySessionServiceConfig sessionStoreConfig) {
    return new InMemorySessionService();
  }

  @Override
  public String type() {
    return MEMORY.name().toLowerCase();
  }
}