package com.agentengine.connectors.core.services;

import com.agentengine.connectors.infra.beans.Connector;
import com.agentengine.util.common.JsonUtils;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Singleton
public final class ConnectorRegistry {
    private final ConcurrentMap<String, Connector> connectorCache;

    public ConnectorRegistry() {
        this.connectorCache = new ConcurrentHashMap<>();
    }

    public Connector get(final String name) {
        return connectorCache.computeIfAbsent(name, _ -> {
            final String content;
            try {
                content = Files.readString(Paths.get("configs/" + name + ".json"));
                return JsonUtils.fromJson(content, Connector.class);
            } catch (IOException e) {
                return null;
            }
        });
    }
}
