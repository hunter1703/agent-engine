package com.agentengine.connectors.core.response;

import com.agentengine.connectors.core.config.ConnectorDefinition;
import com.agentengine.connectors.core.config.ErrorMappingRule;
import com.agentengine.connectors.core.http.HttpResponseData;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public final class DefaultErrorClassifier implements ErrorClassifier {

  private final ResponseExtractor responseExtractor;

  @Inject
  public DefaultErrorClassifier(final ResponseExtractor responseExtractor) {
    this.responseExtractor = responseExtractor;
  }

  @Override
  public ClassifiedError classify(final ConnectorDefinition definition, final HttpResponseData responseData) {
    for (ErrorMappingRule mappingRule : definition.errorMappings()) {
      if (!matches(mappingRule, responseData)) {
        continue;
      }
      final String message = mappingRule.message() != null && !mappingRule.message().isBlank()
          ? mappingRule.message()
          : "Connector request failed";
      final String code = mappingRule.errorCode() == null || mappingRule.errorCode().isBlank()
          ? "HTTP_" + responseData.statusCode()
          : mappingRule.errorCode();
      return new ClassifiedError(code, message, mappingRule.retryable());
    }

    final Object codeValue = responseExtractor.extract(responseData.body(), definition.responseMapping().errorCodeJsonPath());
    final Object messageValue = responseExtractor.extract(responseData.body(), definition.responseMapping().errorMessageJsonPath());
    final String code = codeValue == null ? "HTTP_" + responseData.statusCode() : String.valueOf(codeValue);
    final String message = messageValue == null
        ? "Connector request failed with status " + responseData.statusCode()
        : String.valueOf(messageValue);
    final boolean retryable = definition.retryPolicy().retryableStatusCodes().contains(responseData.statusCode());
    return new ClassifiedError(code, message, retryable);
  }

  private boolean matches(final ErrorMappingRule rule, final HttpResponseData responseData) {
    if (rule.statusCode() != null && rule.statusCode() != responseData.statusCode()) {
      return false;
    }

    if (rule.bodyContains() != null && !rule.bodyContains().isBlank() && !responseData.body().contains(rule.bodyContains())) {
      return false;
    }

    if (rule.bodyJsonPath() != null && !rule.bodyJsonPath().isBlank()) {
      final Object value = responseExtractor.extract(responseData.body(), rule.bodyJsonPath());
      return value != null;
    }

    return true;
  }
}
