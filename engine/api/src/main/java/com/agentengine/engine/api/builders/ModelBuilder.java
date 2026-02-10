package com.agentengine.engine.api.builders;

import com.agentengine.engine.api.beans.config.ModelConfig;
import com.google.adk.models.BaseLlm;

public interface ModelBuilder<L extends BaseLlm> {
  L build(ModelConfig modelConfig);

  String type();
}
