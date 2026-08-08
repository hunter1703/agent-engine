package com.agentengine.connectors.http.builders;

import com.agentengine.connectors.http.TemplatedHttpConnectorSpec;
import com.agentengine.connectors.http.beans.HttpConnectorSpec;
import com.agentengine.connectors.http.executor.HttpConnectorExecutor;
import com.agentengine.connectors.infra.TemplatedConnectorSpec;
import com.agentengine.connectors.infra.builders.ConnectorExecutorBuilder;
import com.agentengine.connectors.infra.executor.ConnectorExecutor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class HttpConnectorExecutorBuilder implements ConnectorExecutorBuilder<HttpConnectorSpec, Map<String, Object>, Map<String, Object>> {
    private final ConcurrentHashMap<HttpConnectorSpec, HttpConnectorExecutor> executorCache = new ConcurrentHashMap<>();

    @Override
    public ConnectorExecutor<Map<String, Object>, Map<String, Object>> build(HttpConnectorSpec spec) {
        return executorCache.computeIfAbsent(spec, _ -> new HttpConnectorExecutor(new TemplatedHttpConnectorSpec(spec)));
    }
}
