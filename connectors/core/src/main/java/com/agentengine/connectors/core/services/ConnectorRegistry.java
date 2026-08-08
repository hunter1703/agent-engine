package com.agentengine.connectors.core.services;

import com.agentengine.connectors.http.beans.HttpConnectorSpec;
import com.agentengine.connectors.infra.beans.Connector;
import com.agentengine.connectors.infra.beans.ConnectorSpec;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.ResourceUtils;
import com.agentengine.util.common.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import jakarta.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Singleton
public final class ConnectorRegistry {
    private static final ObjectMapper CONNECTOR_MAPPER = buildConnectorMapper();

    private static ObjectMapper buildConnectorMapper() {
        final ObjectMapper mapper = JsonUtils.copyMapper();
        mapper.registerSubtypes(new NamedType(HttpConnectorSpec.class, ConnectorSpec.Type.HTTP.name()));
        return mapper;
    }

    private final ConcurrentMap<String, Connector> connectorCache;

    public ConnectorRegistry() {
        this.connectorCache = new ConcurrentHashMap<>();
    }

    public Connector get(final String name) {
        return connectorCache.computeIfAbsent(name, _ -> {
            final String content = ResourceUtils.loadResourceAsString("/connectors/" + name + ".json");
            if (StringUtils.isBlank(content)) {
                return null;
            }
            try {
                return CONNECTOR_MAPPER.readValue(content, Connector.class);
            } catch (Exception e) {
                return null;
            }
        });
    }
}
