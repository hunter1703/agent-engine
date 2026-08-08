package com.agentengine.connectors.api.exceptions;

public class ConnectorException extends RuntimeException {

    public ConnectorException(final String message) {
        super(message);
    }

    public ConnectorException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
