package com.agentengine.util.models;

import com.agentengine.util.models.config.ModelConfig;

public interface ModelLookup {

  ModelConfig getModel(String id);
}
