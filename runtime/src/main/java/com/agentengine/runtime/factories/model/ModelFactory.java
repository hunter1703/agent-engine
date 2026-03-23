package com.agentengine.runtime.factories.model;

import com.agentengine.util.agents.beans.config.ModelConfig;
import com.google.adk.models.BaseLlm;

public interface ModelFactory<L extends BaseLlm> {
  L build(ModelConfig modelConfig);

  String type();
}
