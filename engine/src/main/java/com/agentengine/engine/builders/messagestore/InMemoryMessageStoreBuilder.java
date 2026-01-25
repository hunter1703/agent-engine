package com.agentengine.engine.builders.messagestore;

import com.agentengine.engine.api.MessageStore;
import com.agentengine.engine.api.beans.config.InMemoryMessageStoreConfig;
import com.agentengine.engine.api.beans.config.MessageStoreConfig;
import com.agentengine.engine.api.builders.MessageStoreBuilder;
import com.agentengine.engine.state.InMemoryMessageStore;
import jakarta.inject.Singleton;

import static com.agentengine.engine.api.beans.config.MessageStoreConfig.MessageStoreType.MEMORY;

@Singleton
public class InMemoryMessageStoreBuilder implements MessageStoreBuilder<InMemoryMessageStoreConfig, MessageStore> {

    @Override
    public MessageStore build(final InMemoryMessageStoreConfig messageStoreConfig) {
        return new InMemoryMessageStore();
    }

    @Override
    public String type() {
        return MEMORY.name().toLowerCase();
    }
}