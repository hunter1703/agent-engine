package com.agentengine.connectors.infra.builders;

import com.agentengine.connectors.infra.auth.AuthDecorator;
import com.agentengine.connectors.infra.beans.AuthDecoratorSpec;
import com.agentengine.connectors.infra.beans.Request;
import com.agentengine.util.common.LazyLoader;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Singleton
public class AuthDecoratorFactory {
  private final LazyLoader<ConcurrentMap<AuthDecoratorSpec.Type, AuthDecoratorBuilder<?, ?, ?>>>
      typeVsBuilder;

  // LazyLoader is used here to break a circular dependency at boot time:
  // The connector framework uses auth decorators to authenticate requests.
  // However, the auth decorators themselves need the connector framework to execute token refresh
  // requests.
  // This causes: ConnectorService -> AuthDecoratorFactory -> AuthDecoratorBuilder ->
  // ConnectionRefresher -> ConnectorService.
  //
  // This is inherent because token refresh is itself an HTTP request that benefits from the same
  // connector infrastructure (rate limiting, retries, etc.). An alternative structural solution
  // would be to extract token refreshing into a dedicated HTTP client/service separate from the
  // main connector framework, but that would duplicate HTTP logic and configuration. Deferring the
  // builder initialization with LazyLoader is an elegant way to resolve this without duplication.
  public AuthDecoratorFactory(@Any Instance<AuthDecoratorBuilder<?, ?, ?>> builders) {
    this.typeVsBuilder =
        new LazyLoader<>(
            () -> {
              final ConcurrentMap<AuthDecoratorSpec.Type, AuthDecoratorBuilder<?, ?, ?>> map =
                  new ConcurrentHashMap<>();
              for (AuthDecoratorBuilder<?, ?, ?> builder : builders) {
                if (map.putIfAbsent(builder.getType(), builder) != null) {
                  throw new IllegalStateException(
                      "Duplicate AuthDecoratorBuilder: " + builder.getType());
                }
              }
              return map;
            });
  }

  @SuppressWarnings("unchecked")
  public <I, R extends Request> AuthDecorator<I, R> build(AuthDecoratorSpec spec) {
    if (spec == null) {
      return AuthDecorator.noop();
    }
    final AuthDecoratorBuilder<AuthDecoratorSpec, I, R> builder =
        (AuthDecoratorBuilder<AuthDecoratorSpec, I, R>)
            typeVsBuilder.get().get(AuthDecoratorSpec.Type.valueOfOrUnknown(spec.getType()));
    if (builder == null) {
      throw new IllegalStateException("No AuthDecoratorBuilder: " + spec.getType());
    }
    return builder.build(spec);
  }
}
