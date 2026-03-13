package com.agentengine.connectors.core.response;

import com.agentengine.connectors.core.config.ConnectorDefinition;
import com.agentengine.connectors.core.http.HttpResponseData;
import com.agentengine.connectors.core.runtime.ConnectorExecutionResult;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Set;

@Singleton
public final class DefaultResponseMapper {

  private static final Set<Integer> DEFAULT_SUCCESS_STATUS_CODES = java.util.stream.IntStream.range(200, 300).boxed()
      .collect(java.util.stream.Collectors.toUnmodifiableSet());

  private final ResponseExtractor extractor;
  private final ErrorClassifier errorClassifier;

  @Inject
  public DefaultResponseMapper(final ResponseExtractor responseExtractor, final ErrorClassifier errorClassifier) {
    this.extractor = responseExtractor;
    this.errorClassifier = errorClassifier;
  }

  public ConnectorExecutionResult map(final ConnectorDefinition definition, final HttpResponseData responseData, final String requestUrl,
      final String method) {
    final boolean success = isSuccess(definition, responseData.statusCode());
    final Object data = extractor.extract(responseData.body(), definition.responseMapping().dataJsonPath());
    final Object metadata = extractor.extract(responseData.body(), definition.responseMapping().metadataJsonPath());

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
