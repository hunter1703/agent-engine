package com.agentengine.connectors.core.validation;

import java.util.List;

public record ConfigValidationResult(List<ValidationIssue> issues) {

    public ConfigValidationResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(issue -> issue.severityEnum() == ValidationSeverity.ERROR);
    }

    public boolean isValid() {
        return !hasErrors();
    }

    public List<ValidationIssue> errors() {
        return issues.stream()
                .filter(issue -> issue.severityEnum() == ValidationSeverity.ERROR)
                .toList();
    }
}
