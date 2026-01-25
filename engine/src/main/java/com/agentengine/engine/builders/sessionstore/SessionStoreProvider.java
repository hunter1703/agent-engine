package com.agentengine.engine.builders.sessionstore;

import com.agentengine.engine.api.SessionStore;
import com.agentengine.engine.api.beans.config.SessionStoreConfig;
import com.agentengine.engine.api.builders.SessionStoreBuilder;
import com.agentengine.engine.api.utils.CollectionUtils;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.function.Function;

@Singleton
public class SessionStoreProvider {

  private final Map<String, SessionStoreBuilder<?, ?>> typeVsBuilder;
  private final SessionStoreBuilder<?, ?> defaultBuilder;

  @Inject
  public SessionStoreProvider(final Instance<SessionStoreBuilder<?, ?>> allBuilders,
      final InMemorySessionStoreBuilder inMemorySessionStoreBuilder) {
    this.typeVsBuilder = CollectionUtils.transformToMap(allBuilders.stream().toList(), SessionStoreBuilder::type,
        Function.identity());
    this.defaultBuilder = inMemorySessionStoreBuilder;
  }

  public <C extends SessionStoreConfig, S extends SessionStore> S get(final C config) {
    // noinspection unchecked
    final SessionStoreBuilder<C, S> builder = (SessionStoreBuilder<C, S>) typeVsBuilder.getOrDefault(config.getType(),
        defaultBuilder);
    return builder.build(config);
  }
}