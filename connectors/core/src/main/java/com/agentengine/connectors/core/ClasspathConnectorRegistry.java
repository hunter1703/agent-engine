package com.agentengine.connectors.core;

import com.agentengine.connectors.core.config.ConnectorDefinition;
import com.agentengine.connectors.core.load.ConnectorConfigLoader;
import com.agentengine.connectors.core.load.ConnectorConfigLoadException;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Singleton
public class ClasspathConnectorRegistry implements ConnectorRegistry {
    private final ConnectorConfigLoader loader;

    @Inject
    public ClasspathConnectorRegistry(ConnectorConfigLoader loader) {
        this.loader = loader;
    }

    @Override
    public Optional<ConnectorDefinition> getConnector(String id) {
        final Optional<ConnectorDefinition> jsonDefinition = loadFromResource(id, ".json");
        if (jsonDefinition.isPresent()) {
            return jsonDefinition;
        }

        final Optional<ConnectorDefinition> yamlDefinition = loadFromResource(id, ".yaml");
        if (yamlDefinition.isPresent()) {
            return yamlDefinition;
        }

        return loadFromResource(id, ".yml");
    }

    private Optional<ConnectorDefinition> loadFromResource(final String id, final String extension) {
        final String resourcePath = "/connectors/" + id + extension;
        try (InputStream stream = getClass().getResourceAsStream(resourcePath)) {
            if (stream != null) {
                final String config = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                return Optional.of(loader.load(config));
            }
            return Optional.empty();
        } catch (IOException ex) {
            throw new ConnectorConfigLoadException("Failed to read connector config: " + resourcePath, ex);
        }
    }
}
