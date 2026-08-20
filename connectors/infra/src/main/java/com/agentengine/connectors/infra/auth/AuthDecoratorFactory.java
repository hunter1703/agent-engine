package com.agentengine.connectors.infra.auth;

import com.agentengine.connectors.infra.beans.Request;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Singleton
public class AuthDecoratorFactory {
  private final ConcurrentMap<AuthDecoratorSpec.Type, AuthDecoratorBuilder<?, ?>> typeVsBuilder =
      new ConcurrentHashMap<>();

  public AuthDecoratorFactory(@Any Instance<AuthDecoratorBuilder<?, ?>> builders) {
    for (AuthDecoratorBuilder<?, ?> builder : builders) {
      if (typeVsBuilder.putIfAbsent(builder.getType(), builder) != null) {
        throw new IllegalStateException("Duplicate AuthDecoratorBuilder: " + builder.getType());
      }
    }
  }

  @SuppressWarnings("unchecked")
  public <R extends Request> AuthDecorator<R> build(AuthDecoratorSpec spec) {
    if (spec == null) {
      return AuthDecorator.noop();
    }
    final AuthDecoratorBuilder<AuthDecoratorSpec, R> builder =
        (AuthDecoratorBuilder<AuthDecoratorSpec, R>)
            typeVsBuilder.get(AuthDecoratorSpec.Type.valueOfOrUnknown(spec.getType()));
    if (builder == null) {
      throw new IllegalStateException("No AuthDecoratorBuilder: " + spec.getType());
    }
    return builder.build(spec);
  }
}
