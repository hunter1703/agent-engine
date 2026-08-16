package com.agentengine.connectors.infra.executor;

import com.agentengine.connectors.api.beans.ConnectorResult;

public interface ConnectorExecutor<I, O> {

  ConnectorResult<O> execute(I input);
}
