package com.agentengine.engine.api.builders;

import com.agentengine.engine.api.beans.config.StateStoreConfig;
import com.agentengine.engine.api.StateStore;

public interface StateStoreBuilder<C extends StateStoreConfig, S extends StateStore> {

  S build(C stateStoreConfig);

  String type();
}
