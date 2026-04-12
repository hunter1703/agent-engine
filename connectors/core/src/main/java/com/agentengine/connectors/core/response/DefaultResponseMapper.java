package com.agentengine.connectors.core.response;

import com.agentengine.connectors.core.config.ConnectorDefinition;
import com.agentengine.connectors.core.http.HttpResponseData;
import com.agentengine.connectors.core.runtime.ConnectorExecutionResult;
import com.agentengine.connectors.core.runtime.RequestContext;
import com.agentengine.connectors.core.template.TemplateResolver;
import com.agentengine.util.common.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class DefaultResponseMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultResponseMapper.class);
    private static final Set<Integer> DEFAULT_SUCCESS_STATUS_CODES =
            IntStream.range(200, 300).boxed().collect(Collectors.toUnmodifiableSet());
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {};

    private final ErrorClassifier errorClassifier;
    private final TemplateResolver templateResolver;

    @Inject
    public DefaultResponseMapper(final ErrorClassifier errorClassifier, final TemplateResolver templateResolver) {
        this.errorClassifier = errorClassifier;
        this.templateResolver = templateResolver;
    }

    public ConnectorExecutionResult map(
            final ConnectorDefinition definition,
            final HttpResponseData responseData,
            final String requestUrl,
            final String method) {
        final boolean success = isSuccess(definition, responseData.statusCode());

        final Object parsedResponse = parseResponseBody(responseData.body(), responseData.headers());

        final Map<String, Object> responseVariables = Map.of(
                "response", parsedResponse, "statusCode", responseData.statusCode(), "headers", responseData.headers());
        final RequestContext context = new RequestContext(Map.of(), null, Map.of(), null, Map.of(), responseVariables);

        final Object data = templateResolver
                .resolve(definition.responseMapping().output(), context, null)
                .value();
        final Object metadata = definition.responseMapping().metadata() != null
                ? templateResolver
                        .resolve(definition.responseMapping().metadata(), context, null)
                        .value()
                : null;

        if (success) {
            return new ConnectorExecutionResult(
                    responseData.statusCode(),
                    true,
                    requestUrl,
                    method,
                    responseData.flattenHeaders(),
                    data,
                    metadata,
                    definition.responseMapping().includeRawBody() ? responseData.body() : null,
                    null,
                    null,
                    false);
        }

        final ClassifiedError classifiedError = errorClassifier.classify(definition, responseData);
        return new ConnectorExecutionResult(
                responseData.statusCode(),
                false,
                requestUrl,
                method,
                responseData.flattenHeaders(),
                data,
                metadata,
                definition.responseMapping().includeRawBody() ? responseData.body() : null,
                classifiedError.code(),
                classifiedError.message(),
                classifiedError.retryable());
    }

    private boolean isSuccess(final ConnectorDefinition definition, final int statusCode) {
        if (definition.responseMapping().hasCustomSuccessStatusCodes()) {
            return definition.responseMapping().successStatusCodes().contains(statusCode);
        }
        return DEFAULT_SUCCESS_STATUS_CODES.contains(statusCode);
    }

    private Object parseResponseBody(final String body, final Map<String, java.util.List<String>> headers) {
        if (body == null || body.isBlank()) {
            return body;
        }

        // Check if response is JSON based on Content-Type header
        final boolean isJson = headers.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().equalsIgnoreCase("content-type"))
                .flatMap(entry -> entry.getValue().stream())
                .anyMatch(value -> value != null && value.toLowerCase().contains("application/json"));

        if (!isJson) {
            // If not JSON, return as-is
            return body;
        }

        // Try to parse as JSON
        try {
            return JsonUtils.fromJson(body, MAP_TYPE_REF);
        } catch (Exception ex) {
            LOGGER.debug("Failed to parse response body as JSON, returning as string", ex);
            return body;
        }
    }
}
