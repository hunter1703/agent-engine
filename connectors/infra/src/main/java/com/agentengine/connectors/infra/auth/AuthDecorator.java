package com.agentengine.connectors.infra.auth;

import com.agentengine.connectors.api.beans.Connection;
import com.agentengine.connectors.infra.beans.Request;

public interface AuthDecorator<I, R extends Request> {

  void decorate(Connection connection, I input, R request);

  static <R extends Request, I> AuthDecorator<I, R> noop() {
    return (_, _, _) -> {};
  }
}
