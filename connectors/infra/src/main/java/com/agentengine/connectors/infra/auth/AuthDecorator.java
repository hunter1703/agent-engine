package com.agentengine.connectors.infra.auth;

import com.agentengine.connectors.api.beans.Connection;
import com.agentengine.connectors.infra.beans.Request;

public interface AuthDecorator<R extends Request> {

  void decorate(Connection connection, R request);

  static <R extends Request> AuthDecorator<R> noop() {
    return (_, _) -> {};
  }
}
