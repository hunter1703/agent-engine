package com.agentengine.connectors.core.validation;

import com.agentengine.connectors.core.config.BodyConfig;
import com.agentengine.connectors.core.config.BodyType;
import com.agentengine.connectors.core.config.ConnectorDefinition;
import com.agentengine.connectors.core.config.EndpointConfig;
import com.agentengine.connectors.core.config.HttpMethod;
import com.agentengine.connectors.core.config.PaginationConfig;
import com.agentengine.connectors.core.config.PaginationType;
import com.agentengine.connectors.core.config.ResponseMappingConfig;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Singleton
public final class DefaultConnectorConfigValidator implements ConnectorConfigValidator {

    @Override
    public ConfigValidationResult validate(final ConnectorDefinition definition) {
        final List<ValidationIssue> issues = new ArrayList<>();
        if (definition == null) {
            issues.add(ValidationIssue.error("$", "Connector definition must be provided"));
            return new ConfigValidationResult(issues);
        }

        if (definition.id() == null || definition.id().isBlank()) {
            issues.add(ValidationIssue.error("id", "Connector ID cannot be empty"));
        }

        validateEndpoint(definition.endpoint(), issues);
        validateBody(definition.endpoint(), definition.body(), issues);
        validatePagination(definition.pagination(), issues);
        validateResponseMapping(definition.responseMapping(), issues);

        validateTemplateSyntax("headers", definition.headers(), issues);
        validateTemplateSyntax("query", definition.query(), issues);
        validateTemplateSyntax(
                "body", definition.body() == null ? null : definition.body().template(), issues);

        return new ConfigValidationResult(issues);
    }

    private static void validateEndpoint(final EndpointConfig endpoint, final List<ValidationIssue> issues) {
        if (endpoint == null) {
            issues.add(ValidationIssue.error("endpoint", "Endpoint config is required"));
            return;
        }

        if (endpoint.methodEnum() == HttpMethod.UNKNOWN) {
            issues.add(ValidationIssue.error("endpoint.method", "HTTP method is required"));
        }

        final boolean hasBaseUrl =
                endpoint.baseUrl() != null && !endpoint.baseUrl().isBlank();
        final boolean hasBaseTemplate = endpoint.baseUrlTemplate() != null
                && !endpoint.baseUrlTemplate().isBlank();
        if (!hasBaseUrl && !hasBaseTemplate) {
            issues.add(ValidationIssue.error(
                    "endpoint.baseUrl", "Either endpoint.baseUrl or endpoint.baseUrlTemplate is required"));
        }

        if (hasBaseUrl && hasBaseTemplate) {
            issues.add(ValidationIssue.error(
                    "endpoint.baseUrlTemplate",
                    "Only one of endpoint.baseUrl or endpoint.baseUrlTemplate can be configured"));
        }

        if (endpoint.path() != null
                && !endpoint.path().isBlank()
                && endpoint.pathTemplate() != null
                && !endpoint.pathTemplate().isBlank()) {
            issues.add(ValidationIssue.error(
                    "endpoint.pathTemplate", "Only one of endpoint.path or endpoint.pathTemplate is allowed"));
        }
    }

    private static void validateBody(
            final EndpointConfig endpoint, final BodyConfig bodyConfig, final List<ValidationIssue> issues) {
        if (bodyConfig == null || bodyConfig.template() == null) {
            return;
        }

        final HttpMethod method = endpoint == null ? HttpMethod.UNKNOWN : endpoint.methodEnum();
        if (!method.supportsBody()) {
            issues.add(ValidationIssue.error(
                    "body",
                    "Request body is not allowed for method " + method.name().toUpperCase()));
        }

        if (bodyConfig.typeEnum() == BodyType.UNKNOWN) {
            issues.add(ValidationIssue.error("body.type", "Body type is required when body is configured"));
        }
    }

    private static void validatePagination(final PaginationConfig pagination, final List<ValidationIssue> issues) {
        if (pagination == null || pagination.typeEnum() == PaginationType.NONE) {
            return;
        }

        if (pagination.typeEnum() == PaginationType.UNKNOWN) {
            issues.add(ValidationIssue.error("pagination.type", "Pagination type is required"));
            return;
        }

        switch (pagination.typeEnum()) {
            case PAGE -> {
                requireField(issues, pagination.pageParam(), "pagination.pageParam", "pageParam is required");
                requireField(
                        issues, pagination.pageSizeParam(), "pagination.pageSizeParam", "pageSizeParam is required");
            }
            case OFFSET -> {
                requireField(issues, pagination.offsetParam(), "pagination.offsetParam", "offsetParam is required");
                requireField(issues, pagination.limitParam(), "pagination.limitParam", "limitParam is required");
            }
            case CURSOR -> {
                requireField(issues, pagination.cursorParam(), "pagination.cursorParam", "cursorParam is required");
                requireField(issues, pagination.nextCursor(), "pagination.nextCursor", "nextCursor is required");
            }
            case NEXT_URL ->
                requireField(issues, pagination.nextPageUrl(), "pagination.nextPageUrl", "nextPageUrl is required");
            default -> {
                // no-op
            }
        }
    }

    private static void validateResponseMapping(
            final ResponseMappingConfig mappingConfig, final List<ValidationIssue> issues) {
        if (mappingConfig == null) {
            return;
        }

        mappingConfig.successStatusCodes().forEach(statusCode -> {
            if (statusCode == null || statusCode < 100 || statusCode > 599) {
                issues.add(ValidationIssue.error(
                        "responseMapping.successStatusCodes",
                        "Invalid HTTP status code in successStatusCodes: " + statusCode));
            }
        });
    }

    private static void requireField(
            final List<ValidationIssue> issues, final String value, final String path, final String message) {
        if (value == null || value.isBlank()) {
            issues.add(ValidationIssue.error(path, message));
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateTemplateSyntax(
            final String path, final Object value, final List<ValidationIssue> issues) {
        if (value == null) {
            return;
        }

        if (value instanceof String stringValue) {
            if (!hasBalancedTemplateDelimiters(stringValue)) {
                issues.add(ValidationIssue.error(path, "Template delimiters are unbalanced for value: " + stringValue));
            }
            return;
        }

        if (value instanceof Map<?, ?> mapValue) {
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                validateTemplateSyntax(path + "." + entry.getKey(), entry.getValue(), issues);
            }
            return;
        }

        if (value instanceof Iterable<?> iterable) {
            int index = 0;
            for (Object item : iterable) {
                validateTemplateSyntax(path + "[" + index + "]", item, issues);
                index++;
            }
            return;
        }

        if (value instanceof BodyConfig bodyConfig) {
            validateTemplateSyntax(path + ".template", bodyConfig.template(), issues);
        }
    }

    private static boolean hasBalancedTemplateDelimiters(final String template) {
        return countOccurrences(template, "{{") == countOccurrences(template, "}}")
                && countOccurrences(template, "${") == countOccurrences(template, "}");
    }

    private static int countOccurrences(final String value, final String pattern) {
        int count = 0;
        int start = 0;
        while (true) {
            final int index = value.indexOf(pattern, start);
            if (index < 0) {
                break;
            }
            count++;
            start = index + pattern.length();
        }
        return count;
    }
}
