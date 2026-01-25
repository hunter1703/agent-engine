package com.agentengine.engine.api.builders;

import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.MessageStore;
import com.agentengine.engine.api.beans.config.ContextManagerConfig;
import com.agentengine.engine.tools.Tool;

import java.util.List;

public interface ContextManagerBuilder<C extends ContextManagerConfig, CM extends ContextManager> {

  CM build(String role, C contextConfig, String protocolMessage, List<Tool> availableTools);

  default CM build(String role, C contextConfig, String protocolMessage, List<Tool> availableTools,
      MessageStore messageStore) {
    return build(role, contextConfig, protocolMessage, availableTools);
  }

  String type();
}
