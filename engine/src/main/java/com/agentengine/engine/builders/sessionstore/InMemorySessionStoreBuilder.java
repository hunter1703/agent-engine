package com.agentengine.engine.builders.sessionstore;

import com.agentengine.engine.api.SessionStore;
import com.agentengine.engine.api.beans.config.InMemorySessionStoreConfig;
import com.agentengine.engine.api.beans.config.SessionStoreConfig;
import com.agentengine.engine.api.builders.SessionStoreBuilder;
import com.agentengine.engine.state.InMemorySessionStore;
import jakarta.inject.Singleton;

import static com.agentengine.engine.api.beans.config.SessionStoreConfig.SessionStoreType.MEMORY;

@Singleton
public class InMemorySessionStoreBuilder implements SessionStoreBuilder<InMemorySessionStoreConfig, SessionStore> {

    @Override
    public SessionStore build(final InMemorySessionStoreConfig sessionStoreConfig) {
        return new InMemorySessionStore();
    }

    @Override
    public String type() {
        return MEMORY.name().toLowerCase();
    }
}