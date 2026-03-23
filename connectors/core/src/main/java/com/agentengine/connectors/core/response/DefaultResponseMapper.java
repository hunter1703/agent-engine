package com.agentengine.connectors.core.response;

import com.agentengine.connectors.core.config.ConnectorDefinition;
import com.agentengine.connectors.core.http.HttpResponseData;
import com.agentengine.connectors.core.runtime.ConnectorExecutionResult;
import com.agentengine.connectors.core.runtime.RequestContext;
import com.agentengine.connectors.core.template.TemplateResolver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Singleton
public final class DefaultResponseMapper {

  private static final Set<Integer> DEFAULT_SUCCESS_STATUS_CODES = IntStream.range(200, 300).boxed().collect(Collectors.toUnmodifiableSet());

  private final ErrorClassifier errorClassifier;
  private final TemplateResolver templateResolver;

  @Inject
  public DefaultResponseMapper(final ErrorClassifier errorClassifier, final TemplateResolver templateResolver) {
    this.errorClassifier = errorClassifier;
    this.templateResolver = templateResolver;
  }

  public ConnectorExecutionResult map(final ConnectorDefinition definition, final HttpResponseData responseData, final String requestUrl,
      final String method) {
    final boolean success = isSuccess(definition, responseData.statusCode());

    final Map<String, Object> templateVariables = Map.of("response", responseData.body(), "statusCode", responseData.statusCode(),
        "headers", responseData.headers());
    final RequestContext context = new RequestContext(templateVariables, null, Map.of(), null, Map.of(), Map.of());

    final Object data = templateResolver.resolve(definition.responseMapping().output(), context, null).value();
    final Object metadata = definition.responseMapping().metadata() != null
        ? templateResolver.resolve(definition.responseMapping().metadata(), context, null).value()
        : null;

    if (success) {
      return new ConnectorExecutionResult(responseData.statusCode(), true, requestUrl, method, responseData.flattenHeaders(), data,
          metadata, definition.responseMapping().includeRawBody() ? responseData.body() : null, null, null, false);
    }

    final ClassifiedError classifiedError = errorClassifier.classify(definition, responseData);
    return new ConnectorExecutionResult(responseData.statusCode(), false, requestUrl, method, responseData.flattenHeaders(), data, metadata,
        definition.responseMapping().includeRawBody() ? responseData.body() : null, classifiedError.code(), classifiedError.message(),
        classifiedError.retryable());
  }

  private boolean isSuccess(final ConnectorDefinition definition, final int statusCode) {
    if (definition.responseMapping().hasCustomSuccessStatusCodes()) {
      return definition.responseMapping().successStatusCodes().contains(statusCode);
    }
    return DEFAULT_SUCCESS_STATUS_CODES.contains(statusCode);
  }
}
