package com.agentengine.connectors.core.runtime;

import com.agentengine.connectors.core.config.ConnectorDefinition;

public interface ConnectorExecutor {

    ConnectorExecutionResult executeOnce(ConnectorDefinition definition, RequestContext context);

    PaginatedExecutionResult executeAllPages(ConnectorDefinition definition, RequestContext context);
}
