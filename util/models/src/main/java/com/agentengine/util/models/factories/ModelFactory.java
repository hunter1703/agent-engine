package com.agentengine.util.models.factories;

import com.agentengine.util.models.config.ModelConfig;
import com.google.adk.models.BaseLlm;

public interface ModelFactory<L extends BaseLlm> {
  L build(ModelConfig modelConfig);

  String type();
}
