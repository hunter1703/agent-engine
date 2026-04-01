package com.agentengine.connectors.core.auth;

import com.agentengine.connectors.core.ConnectorAuthMaterialProvider;
import com.agentengine.connectors.core.repository.ConnectionRepository;
import com.agentengine.connectors.core.runtime.Connection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;

@Singleton
public class ConnectorAuthMaterialProviderImpl implements ConnectorAuthMaterialProvider {

    private final ConnectionRepository connectionRepository;

    @Inject
    public ConnectorAuthMaterialProviderImpl(final ConnectionRepository connectionRepository) {
        this.connectionRepository = connectionRepository;
    }

    @Override
    public Map<String, Object> resolve(final String appName) {
        final Connection config = connectionRepository.findByAppName(appName);
        if (config == null) {
            return Map.of();
        }
        return config.inputs() == null ? Map.of() : Map.copyOf(config.inputs());
    }
}
