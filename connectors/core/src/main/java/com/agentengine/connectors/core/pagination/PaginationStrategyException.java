package com.agentengine.connectors.core.pagination;

public final class PaginationStrategyException extends RuntimeException {

    public PaginationStrategyException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public PaginationStrategyException(final String message) {
        super(message);
    }
}
