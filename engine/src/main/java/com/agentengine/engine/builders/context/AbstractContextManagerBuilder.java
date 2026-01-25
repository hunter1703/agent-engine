package com.agentengine.engine.builders.context;

import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.MessageStore;
import com.agentengine.engine.api.beans.config.ContextManagerConfig;
import com.agentengine.engine.api.builders.ContextManagerBuilder;
import com.agentengine.engine.builders.messagestore.MessageStoreProvider;

public abstract class AbstractContextManagerBuilder<C extends ContextManagerConfig, CM extends ContextManager> implements ContextManagerBuilder<C, CM> {
    protected final MessageStoreProvider messageStoreProvider;

    public AbstractContextManagerBuilder(MessageStoreProvider messageStoreProvider) {
        this.messageStoreProvider = messageStoreProvider;
    }
}
