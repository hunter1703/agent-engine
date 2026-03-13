package com.agentengine.engine.plugin.factories;

import com.agentengine.engine.api.beans.config.ModelConfig;
import com.google.adk.models.BaseLlm;

public interface ModelFactory<L extends BaseLlm> {
  L build(ModelConfig modelConfig);

  String type();
}
