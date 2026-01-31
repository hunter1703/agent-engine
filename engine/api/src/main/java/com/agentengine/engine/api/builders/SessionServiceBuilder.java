package com.agentengine.engine.api.builders;

import com.agentengine.engine.api.beans.config.SessionServiceConfig;
import com.google.adk.sessions.BaseSessionService;

public interface SessionServiceBuilder<C extends SessionServiceConfig, S extends BaseSessionService> {

  S build(C config);

  String type();
}