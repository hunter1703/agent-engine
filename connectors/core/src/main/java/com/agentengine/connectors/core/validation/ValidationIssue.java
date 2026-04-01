package com.agentengine.connectors.core.validation;

public record ValidationIssue(String path, String message, String severity) {

    public ValidationIssue {
        severity = severity == null || severity.isBlank() ? ValidationSeverity.ERROR.name() : severity;
        path = path == null ? "" : path;
    }

    public ValidationIssue(final String path, final String message, final ValidationSeverity severity) {
        this(path, message, severity == null ? null : severity.name());
    }

    public static ValidationIssue error(final String path, final String message) {
        return new ValidationIssue(path, message, ValidationSeverity.ERROR);
    }

    public static ValidationIssue warning(final String path, final String message) {
        return new ValidationIssue(path, message, ValidationSeverity.WARNING);
    }

    public ValidationSeverity severityEnum() {
        return ValidationSeverity.valueOfOrDefault(severity);
    }
}
