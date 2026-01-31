package com.agentengine.engine.builders.state;

import com.agentengine.engine.api.beans.config.SessionServiceConfig;
import com.agentengine.engine.api.builders.SessionServiceBuilder;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.google.adk.sessions.BaseSessionService;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.function.Function;

@Singleton
public class SessionServiceProvider {

  private final Map<String, SessionServiceBuilder<?, ?>> typeVsBuilder;
  private final SessionServiceBuilder<?, ?> defaultBuilder;

  @Inject
  public SessionServiceProvider(final Instance<SessionServiceBuilder<?, ?>> allBuilders,
                                final InMemorySessionServiceBuilder inMemorySessionServiceBuilder) {
    this.typeVsBuilder = CollectionUtils.transformToMap(allBuilders.stream().toList(), SessionServiceBuilder::type,
        Function.identity());
    this.defaultBuilder = inMemorySessionServiceBuilder;
  }

  public <C extends SessionServiceConfig, S extends BaseSessionService> S get(final C config) {
    //noinspection unchecked
    final SessionServiceBuilder<C, S> builder = (SessionServiceBuilder<C, S>) typeVsBuilder.getOrDefault(config.getType(), defaultBuilder);
    return builder.build(config);
  }
}