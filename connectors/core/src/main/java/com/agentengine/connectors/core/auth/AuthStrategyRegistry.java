package com.agentengine.connectors.core.auth;

import com.agentengine.connectors.core.config.AuthConfig;
import com.agentengine.connectors.core.config.AuthType;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Singleton
public final class AuthStrategyRegistry {

    private final Map<AuthType, AuthStrategy> strategies;

    @Inject
    public AuthStrategyRegistry(final Instance<AuthStrategy> strategies) {
        this(toCollection(strategies));
    }

    public AuthStrategyRegistry(final Collection<AuthStrategy> customStrategies) {
        final Map<AuthType, AuthStrategy> strategyMap = new EnumMap<>(AuthType.class);
        customStrategies.forEach(strategy -> strategyMap.put(strategy.type(), strategy));
        this.strategies = Map.copyOf(strategyMap);
    }

    public void apply(final AuthRequestContext requestContext) {
        final AuthConfig configured = requestContext.authConfig();
        final AuthConfig effectiveConfig = configured == null
                ? new AuthConfig(
                        AuthType.NONE, null, null, null, null, null, null, null, null, null, null, null, null, Map.of())
                : configured;
        final AuthType effectiveType =
                effectiveConfig.typeEnum() == AuthType.UNKNOWN ? AuthType.NONE : effectiveConfig.typeEnum();
        final AuthStrategy strategy = strategies.getOrDefault(effectiveType, strategies.get(AuthType.NONE));
        if (strategy == null) {
            throw new AuthStrategyException("No auth strategy available for type: " + effectiveType);
        }
        strategy.apply(new AuthRequestContext(
                effectiveConfig,
                requestContext.requestContext(),
                requestContext.templateResolver(),
                requestContext.strictUnresolvedVariables(),
                requestContext.headers(),
                requestContext.queryParams()));
    }

    private static Collection<AuthStrategy> toCollection(final Instance<AuthStrategy> strategies) {
        final List<AuthStrategy> resolved = new ArrayList<>();
        strategies.forEach(resolved::add);
        return resolved;
    }
}
