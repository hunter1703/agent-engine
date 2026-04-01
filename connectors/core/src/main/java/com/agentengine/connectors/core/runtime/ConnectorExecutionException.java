package com.agentengine.connectors.core.runtime;

public final class ConnectorExecutionException extends ConnectorException {

    public ConnectorExecutionException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public ConnectorExecutionException(final String message) {
        super(message);
    }
}
