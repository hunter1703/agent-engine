package com.agentengine.engine.builders.context;

import com.agentengine.engine.api.beans.config.*;
import com.agentengine.engine.api.StateStore;
import com.agentengine.engine.builders.state.StateStoreProvider;
import com.agentengine.engine.context.LastNContextManager;
import com.agentengine.engine.tools.Tool;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class LastNContextManagerBuilder extends AbstractContextManagerBuilder<LastNContextManagerConfig, LastNContextManager> {

    public LastNContextManagerBuilder(final StateStoreProvider stateStoreProvider) {
        super(stateStoreProvider);
    }

    @Override
    public LastNContextManager build(final LastNContextManagerConfig contextConfig, final String protocolMessage, final List<Tool> tools) {
        final StateStore stateStore = stateStoreProvider.get(contextConfig.getStateStore());
        return new LastNContextManager(stateStore, contextConfig.getSystemPrompt(), protocolMessage, tools, contextConfig.getKeepLast());
    }

    @Override
    public String type() {
        return ContextManagerConfig.ContextType.LAST_N.name().toLowerCase();
    }
}
