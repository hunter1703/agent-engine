package com.agentengine.engine.api.builders;

import com.agentengine.engine.api.SessionStore;
import com.agentengine.engine.api.beans.config.SessionStoreConfig;

public interface SessionStoreBuilder<C extends SessionStoreConfig, S extends SessionStore> {

  S build(C config);

  String type();
}