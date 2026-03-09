package com.agentengine.connectors.core.validation;

public record ValidationIssue(String path, String message, ValidationSeverity severity) {

  public ValidationIssue {
    severity = severity == null ? ValidationSeverity.ERROR : severity;
    path = path == null ? "" : path;
  }

  public static ValidationIssue error(final String path, final String message) {
    return new ValidationIssue(path, message, ValidationSeverity.ERROR);
  }

  public static ValidationIssue warning(final String path, final String message) {
    return new ValidationIssue(path, message, ValidationSeverity.WARNING);
  }
}
