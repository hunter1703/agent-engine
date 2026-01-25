package com.agentengine.engine.builders.context;

import com.agentengine.engine.api.MessageStore;
import com.agentengine.engine.api.beans.config.*;
import com.agentengine.engine.builders.messagestore.MessageStoreProvider;
import com.agentengine.engine.context.LastNContextManager;
import com.agentengine.engine.tools.Tool;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class LastNContextManagerBuilder extends AbstractContextManagerBuilder<LastNContextManagerConfig, LastNContextManager> {

    public LastNContextManagerBuilder(final MessageStoreProvider messageStoreProvider) {
        super(messageStoreProvider);
    }

    @Override
    public LastNContextManager build(final String role, final LastNContextManagerConfig contextConfig, final String protocolMessage, final List<Tool> tools) {
        return build(role, contextConfig, protocolMessage, tools, null);
    }

    @Override
    public LastNContextManager build(final String role, final LastNContextManagerConfig contextConfig,
                                     final String protocolMessage, final List<Tool> tools,
                                     final MessageStore messageStore) {
        final MessageStore resolved = messageStore == null
                ? messageStoreProvider.get(contextConfig.getMessageStore())
                : messageStore;
        return new LastNContextManager(role, resolved, contextConfig.getSystemPrompt(), protocolMessage, tools,
                contextConfig.getKeepLast());
    }

    @Override
    public String type() {
        return ContextManagerConfig.ContextType.LAST_N.name().toLowerCase();
    }
}
