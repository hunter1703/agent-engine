package com.agentengine.connectors.core.template;

public final class UnresolvedVariableException extends RuntimeException {

    public UnresolvedVariableException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
