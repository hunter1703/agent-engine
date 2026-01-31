package com.agentengine.engine.api.builders;

import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.google.adk.models.BaseLlm;
import com.google.adk.sessions.BaseSessionService;

public interface ModelBuilder<L extends BaseLlm> {
  L build(String agentId, AgentModelConfig config);

  String type();
}
