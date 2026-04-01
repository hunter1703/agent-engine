package com.agentengine.connectors.core.validation;

import java.util.List;

public class ConnectorConfigValidationException extends RuntimeException {

    private final List<ValidationIssue> issues;

    public ConnectorConfigValidationException(final List<ValidationIssue> issues) {
        super("Connector config validation failed with " + issues.size() + " issue(s)");
        this.issues = List.copyOf(issues);
    }

    public List<ValidationIssue> issues() {
        return issues;
    }
}
