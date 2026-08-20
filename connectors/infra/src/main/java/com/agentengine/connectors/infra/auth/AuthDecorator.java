package com.agentengine.connectors.infra.auth;

import com.agentengine.connectors.infra.beans.Request;

public interface AuthDecorator<R extends Request> {

  void decorate(R request);

  static <R extends Request> AuthDecorator<R> noop() {
    return request -> {};
  }
}
