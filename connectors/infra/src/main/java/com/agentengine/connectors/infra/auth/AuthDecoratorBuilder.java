package com.agentengine.connectors.infra.auth;

import com.agentengine.connectors.infra.beans.Request;

public interface AuthDecoratorBuilder<Spec extends AuthDecoratorSpec, R extends Request> {

  AuthDecorator<R> build(Spec spec);

  AuthDecoratorSpec.Type getType();
}
