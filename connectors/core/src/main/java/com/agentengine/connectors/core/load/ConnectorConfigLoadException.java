package com.agentengine.connectors.core.load;

public class ConnectorConfigLoadException extends RuntimeException {

    public ConnectorConfigLoadException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public ConnectorConfigLoadException(final String message) {
        super(message);
    }
}
