package com.agentengine.connectors.core.response;

import com.agentengine.connectors.core.config.ConnectorDefinition;
import com.agentengine.connectors.core.config.ErrorMappingRule;
import com.agentengine.connectors.core.http.HttpResponseData;
import com.agentengine.connectors.core.runtime.RequestContext;
import com.agentengine.connectors.core.template.TemplateResolver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;

@Singleton
public final class DefaultErrorClassifier implements ErrorClassifier {

    private final TemplateResolver templateResolver;

    @Inject
    public DefaultErrorClassifier(final TemplateResolver templateResolver) {
        this.templateResolver = templateResolver;
    }

    @Override
    public ClassifiedError classify(final ConnectorDefinition definition, final HttpResponseData responseData) {
        for (ErrorMappingRule mappingRule : definition.errorMappings()) {
            if (!matches(mappingRule, responseData)) {
                continue;
            }
            final String message =
                    mappingRule.message() != null && !mappingRule.message().isBlank()
                            ? mappingRule.message()
                            : "Connector request failed";
            final String code =
                    mappingRule.errorCode() == null || mappingRule.errorCode().isBlank()
                            ? "HTTP_" + responseData.statusCode()
                            : mappingRule.errorCode();
            return new ClassifiedError(code, message, mappingRule.retryable());
        }

        final Map<String, Object> responseVariables = Map.of(
                "response",
                responseData.body(),
                "statusCode",
                responseData.statusCode(),
                "headers",
                responseData.headers());
        final RequestContext context = new RequestContext(Map.of(), null, Map.of(), null, Map.of(), responseVariables);

        final Object codeValue = definition.responseMapping().errorCode() != null
                ? templateResolver
                        .resolve(definition.responseMapping().errorCode(), context, null)
                        .value()
                : null;
        final Object messageValue = definition.responseMapping().errorMessage() != null
                ? templateResolver
                        .resolve(definition.responseMapping().errorMessage(), context, null)
                        .value()
                : null;

        final String code = codeValue == null ? "HTTP_" + responseData.statusCode() : String.valueOf(codeValue);
        final String message = messageValue == null
                ? "Connector request failed with status " + responseData.statusCode()
                : String.valueOf(messageValue);
        final boolean retryable =
                definition.retryPolicy().retryableStatusCodes().contains(responseData.statusCode());
        return new ClassifiedError(code, message, retryable);
    }

    private boolean matches(final ErrorMappingRule rule, final HttpResponseData responseData) {
        if (rule.statusCode() != null && rule.statusCode() != responseData.statusCode()) {
            return false;
        }

        if (rule.bodyContains() != null
                && !rule.bodyContains().isBlank()
                && !responseData.body().contains(rule.bodyContains())) {
            return false;
        }

        if (rule.body() != null && !rule.body().isBlank()) {
            final Map<String, Object> responseVariables = Map.of("response", responseData.body());
            final RequestContext context =
                    new RequestContext(Map.of(), null, Map.of(), null, Map.of(), responseVariables);
            final Object value =
                    templateResolver.resolve(rule.body(), context, null).value();
            return value != null;
        }

        return true;
    }
}
