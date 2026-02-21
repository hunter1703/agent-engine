package com.agentengine.engine.builders.state;

import static com.agentengine.engine.api.beans.config.SessionServiceConfig.SessionServiceType.MEMORY;

import com.agentengine.engine.api.beans.config.InMemorySessionServiceConfig;
import com.agentengine.engine.api.builders.SessionServiceBuilder;
import com.google.adk.sessions.InMemorySessionService;
import jakarta.inject.Singleton;

@Singleton
public class InMemorySessionServiceBuilder
    implements SessionServiceBuilder<InMemorySessionServiceConfig, InMemorySessionService> {

  private static final InMemorySessionService SHARED_INSTANCE = new InMemorySessionService();

  @Override
  public InMemorySessionService build(final InMemorySessionServiceConfig sessionStoreConfig) {
    return SHARED_INSTANCE;
  }

  @Override
  public String type() {
    return MEMORY.name().toLowerCase();
  }
}
