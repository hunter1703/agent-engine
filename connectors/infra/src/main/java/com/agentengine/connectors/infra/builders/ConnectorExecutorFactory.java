package com.agentengine.connectors.infra.builders;

import com.agentengine.connectors.infra.beans.ConnectorSpec;
import com.agentengine.connectors.infra.executor.ConnectorExecutor;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Singleton
public class ConnectorExecutorFactory {
  private final ConcurrentMap<ConnectorSpec.Type, ConnectorExecutorBuilder<?, ?, ?>> typeVsBuilder =
      new ConcurrentHashMap<>();

  public ConnectorExecutorFactory(@Any Instance<ConnectorExecutorBuilder<?, ?, ?>> builders) {
    for (ConnectorExecutorBuilder<?, ?, ?> builder : builders) {
      if (typeVsBuilder.putIfAbsent(builder.getType(), builder) != null) {
        throw new IllegalStateException("Duplicate ConnectorExecutorBuilder: " + builder.getType());
      }
    }
  }

  @SuppressWarnings("unchecked")
  public <I, O> ConnectorExecutor<I, O> build(ConnectorSpec spec) {
    final ConnectorExecutorBuilder<ConnectorSpec, I, O> builder =
        (ConnectorExecutorBuilder<ConnectorSpec, I, O>)
            typeVsBuilder.get(ConnectorSpec.Type.valueOfOrUnknown(spec.getType()));
    if (builder == null) {
      throw new IllegalStateException("No ConnectorExecutorBuilder: " + spec.getType());
    }
    return builder.build(spec);
  }
}
