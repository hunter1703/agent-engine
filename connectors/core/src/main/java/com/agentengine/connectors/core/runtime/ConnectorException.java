package com.agentengine.connectors.core.runtime;

public class ConnectorException extends RuntimeException {

    public ConnectorException(final String message) {
        super(message);
    }

    public ConnectorException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
