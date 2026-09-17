package com.agentengine.connectors.infra.builders;

import com.agentengine.connectors.infra.auth.AuthDecorator;
import com.agentengine.connectors.infra.beans.AuthDecoratorSpec;
import com.agentengine.connectors.infra.beans.Request;

public interface AuthDecoratorBuilder<Spec extends AuthDecoratorSpec, R extends Request> {

  AuthDecorator<R> build(Spec spec);

  AuthDecoratorSpec.Type getType();
}
