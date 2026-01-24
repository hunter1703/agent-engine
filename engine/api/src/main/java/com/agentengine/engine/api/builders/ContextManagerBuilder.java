package com.agentengine.engine.api.builders;

import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.beans.config.ContextManagerConfig;
import com.agentengine.engine.tools.Tool;

import java.util.List;

public interface ContextManagerBuilder<C extends ContextManagerConfig, CM extends ContextManager> {

    CM build(C contextConfig, String protocolMessage, List<Tool> availableTools);

    String type();
}
