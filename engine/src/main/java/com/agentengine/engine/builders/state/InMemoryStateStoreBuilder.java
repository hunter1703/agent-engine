package com.agentengine.engine.builders.state;

import com.agentengine.engine.api.beans.config.MemoryStateStoreConfig;
import com.agentengine.engine.api.beans.config.StateStoreConfig;
import com.agentengine.engine.api.builders.StateStoreBuilder;
import com.agentengine.engine.state.InMemoryStateStore;
import jakarta.inject.Singleton;

@Singleton
public class InMemoryStateStoreBuilder implements StateStoreBuilder<MemoryStateStoreConfig, InMemoryStateStore> {
    @Override
    public InMemoryStateStore build(final MemoryStateStoreConfig stateStoreConfig) {
        return new InMemoryStateStore();
    }

    @Override
    public String type() {
        return StateStoreConfig.StateStoreType.MEMORY.name();
    }
}
