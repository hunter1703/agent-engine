package com.agentengine.connectors.infra.builders;

import com.agentengine.connectors.infra.beans.ExecutorSpec;
import com.agentengine.connectors.infra.executor.ConnectorExecutor;

public interface ConnectorExecutorBuilder<Spec extends ExecutorSpec, I, O> {

  ConnectorExecutor<I, O> build(BuildContext<Spec> context);

  ExecutorSpec.Type getType();
}
