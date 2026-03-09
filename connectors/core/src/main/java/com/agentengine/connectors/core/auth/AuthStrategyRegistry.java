package com.agentengine.connectors.core.auth;

import com.agentengine.connectors.core.config.AuthConfig;
import com.agentengine.connectors.core.config.AuthType;
import com.agentengine.connectors.core.runtime.RequestContext;
import com.agentengine.connectors.core.template.TemplateResolver;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public final class AuthStrategyRegistry {

  private final Map<AuthType, AuthStrategy> strategies;

  @Inject
  public AuthStrategyRegistry() {
    this(
        java.util.List.of(
            new NoAuthStrategy(),
            new ApiKeyHeaderAuthStrategy(),
            new BearerTokenAuthStrategy(),
            new BasicAuthStrategy(),
            new QueryParamAuthStrategy()));
  }

  public AuthStrategyRegistry(final Collection<AuthStrategy> customStrategies) {
    final Map<AuthType, AuthStrategy> strategyMap = new EnumMap<>(AuthType.class);
    customStrategies.forEach(strategy -> strategyMap.put(strategy.type(), strategy));
    strategies = Map.copyOf(strategyMap);
  }

  public void apply(
      final AuthConfig authConfig,
      final RequestContext context,
      final TemplateResolver templateResolver,
      final boolean strictUnresolvedVariables,
      final Map<String, String> headers,
      final Map<String, String> queryParams) {
    final AuthConfig effectiveConfig = authConfig == null ? new AuthConfig(AuthType.NONE, null, null, null, null, null, null, null, null, null, null, null, null, Map.of()) : authConfig;
    final AuthType effectiveType = effectiveConfig.type() == null ? AuthType.NONE : effectiveConfig.type();
    final AuthStrategy strategy = strategies.getOrDefault(effectiveType, strategies.get(AuthType.NONE));
    if (strategy == null) {
      throw new AuthStrategyException("No auth strategy available for type: " + effectiveType);
    }
    strategy.apply(
        effectiveConfig,
        context,
        templateResolver,
        strictUnresolvedVariables,
        headers,
        queryParams);
  }
}
