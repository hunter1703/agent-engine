package com.agentengine.engine.builders.state;

import com.agentengine.engine.api.beans.config.StateStoreConfig;
import com.agentengine.engine.api.builders.StateStoreBuilder;
import com.agentengine.engine.api.StateStore;
import com.agentengine.engine.api.utils.CollectionUtils;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.function.Function;

@Singleton
public class StateStoreProvider {

    private final Map<String, StateStoreBuilder<?, ?>> nameVsBuilder;
    private final InMemoryStateStoreBuilder defaultBuilder;

    @Inject
    public StateStoreProvider(final Instance<StateStoreBuilder<?, ?>> allBuilders,
                              final InMemoryStateStoreBuilder inMemoryStateStoreBuilder) {
        this.nameVsBuilder = CollectionUtils.transformToMap(allBuilders.stream().toList(), StateStoreBuilder::type,
                Function.identity());
        this.defaultBuilder = inMemoryStateStoreBuilder;
    }

    public <C extends StateStoreConfig, S extends StateStore> S get(final C config) {
        //noinspection unchecked
        final StateStoreBuilder<C, S> builder = (StateStoreBuilder<C, S>) nameVsBuilder.getOrDefault(config.getType(), defaultBuilder);
        return builder.build(config);
    }
}
