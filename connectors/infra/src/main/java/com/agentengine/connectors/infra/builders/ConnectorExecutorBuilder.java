package com.agentengine.connectors.infra.builders;

import com.agentengine.connectors.infra.beans.ConnectorSpec;
import com.agentengine.connectors.infra.executor.ConnectorExecutor;

public interface ConnectorExecutorBuilder<Spec extends ConnectorSpec, I, O> {

  ConnectorExecutor<I, O> build(Spec spec);

  ConnectorSpec.Type getType();
}
