package com.agentengine.connectors.infra.builders;

import com.agentengine.connectors.infra.beans.ConnectorSpec;
import com.agentengine.connectors.infra.beans.ExecutorSpec;
import com.agentengine.connectors.infra.executor.ConnectorExecutor;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Singleton
public class ConnectorExecutorFactory {
  private final ConcurrentMap<ExecutorSpec.Type, ConnectorExecutorBuilder<?, ?, ?>> typeVsBuilder =
      new ConcurrentHashMap<>();

  public ConnectorExecutorFactory(@Any Instance<ConnectorExecutorBuilder<?, ?, ?>> builders) {
    for (ConnectorExecutorBuilder<?, ?, ?> builder : builders) {
      if (typeVsBuilder.putIfAbsent(builder.getType(), builder) != null) {
        throw new IllegalStateException("Duplicate ConnectorExecutorBuilder: " + builder.getType());
      }
    }
  }

  @SuppressWarnings("unchecked")
  public <I, O> ConnectorExecutor<I, O> build(final ConnectorSpec spec) {
    final ExecutorSpec executorSpec = spec.getExecutor();
    final ConnectorExecutorBuilder<ExecutorSpec, I, O> builder =
        (ConnectorExecutorBuilder<ExecutorSpec, I, O>)
            typeVsBuilder.get(ExecutorSpec.Type.valueOfOrUnknown(executorSpec.getType()));
    if (builder == null) {
      throw new IllegalStateException("No ConnectorExecutorBuilder: " + executorSpec.getType());
    }
    return builder.build(executorSpec, spec);
  }
}
