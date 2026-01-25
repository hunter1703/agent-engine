package com.agentengine.engine.api.builders;

import com.agentengine.engine.api.MessageStore;
import com.agentengine.engine.api.beans.config.MessageStoreConfig;

public interface MessageStoreBuilder<C extends MessageStoreConfig, S extends MessageStore> {

  S build(C config);

  String type();
}