package com.agentengine.connectors.core.factories;

import com.agentengine.connectors.infra.beans.ConnectorSpec;
import com.agentengine.connectors.infra.builders.ConnectorExecutorBuilder;
import com.agentengine.connectors.infra.executor.ConnectorExecutor;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Singleton
public class ConnectorExecutorFactory {
    private final ConcurrentMap<ConnectorSpec.Type, ConnectorExecutorBuilder<? super ConnectorSpec, ?, ?>> typeVsBuilder = new ConcurrentHashMap<>();

    public ConnectorExecutorFactory(@Any Instance<ConnectorExecutorBuilder<? super ConnectorSpec, ?, ?>> builders) {
        for (ConnectorExecutorBuilder<? super ConnectorSpec, ?, ?> builder : builders) {
            if (typeVsBuilder.putIfAbsent(builder.getType(), builder) != null) {
                throw new IllegalStateException("Duplicate ConnectorExecutorBuilder: " + builder.getType());
            }
        }
    }

    public <I, O> ConnectorExecutor<I, O> build(ConnectorSpec spec) {
        final ConnectorExecutorBuilder<? super ConnectorSpec, ?, ?> builder = typeVsBuilder.get(ConnectorSpec.Type.valueOfOrUnknown(spec.getType()));
        if (builder == null) {
            throw new IllegalStateException("No ConnectorExecutorBuilder: " + spec.getType());
        }
        //noinspection unchecked
        return (ConnectorExecutor<I, O>) builder.build(spec);
    }
}
