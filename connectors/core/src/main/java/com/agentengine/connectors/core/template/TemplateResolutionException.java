package com.agentengine.connectors.core.template;

public class TemplateResolutionException extends RuntimeException {

    public TemplateResolutionException(final String message) {
        super(message);
    }

    public TemplateResolutionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
