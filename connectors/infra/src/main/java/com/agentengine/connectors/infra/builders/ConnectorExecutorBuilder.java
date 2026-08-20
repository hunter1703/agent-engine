package com.agentengine.connectors.infra.builders;

import com.agentengine.connectors.infra.beans.ConnectorSpec;
import com.agentengine.connectors.infra.beans.ExecutorSpec;
import com.agentengine.connectors.infra.executor.ConnectorExecutor;

public interface ConnectorExecutorBuilder<Spec extends ExecutorSpec, I, O> {

  ConnectorExecutor<I, O> build(Spec spec, ConnectorSpec connectorSpec);

  ExecutorSpec.Type getType();
}
