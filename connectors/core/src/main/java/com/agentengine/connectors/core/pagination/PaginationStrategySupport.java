package com.agentengine.connectors.core.pagination;

import com.agentengine.connectors.core.runtime.RequestContext;
import com.agentengine.connectors.core.template.*;
import java.util.Collection;
import java.util.Map;

final class PaginationStrategySupport {

    private static final TemplateResolver templateResolver = new TemplateResolver() {
        @Override
        public ResolvedValue resolve(
                final Object template, final RequestContext context, final TemplateResolutionOptions options) {
            final GroovySandboxEvaluator evaluator = new GroovySandboxEvaluator();
            final Map<String, Object> variables = context.toTemplateVariables();
            if (template == null) {
                return ResolvedValue.nullValue();
            }
            if (template instanceof String stringTemplate) {
                try {
                    final Object value = evaluator.evaluate(stringTemplate, variables);
                    return value == null ? ResolvedValue.nullValue() : ResolvedValue.resolved(value);
                } catch (UnresolvedVariableException ex) {
                    return ResolvedValue.unresolved();
                }
            }
            return ResolvedValue.resolved(template);
        }
    };

    private PaginationStrategySupport() {}

    public static boolean isTerminalData(final Object mappedData) {
        if (mappedData == null) {
            return true;
        }
        if (mappedData instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        return false;
    }

    public static String extractString(final String template, final String responseBody) {
        if (template == null || template.isBlank() || responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            final Map<String, Object> templateVariables = Map.of("response", responseBody);
            final RequestContext context =
                    new RequestContext(templateVariables, null, Map.of(), null, Map.of(), Map.of());
            final ResolvedValue resolved = templateResolver.resolve(template, context, null);
            return resolved.value() == null ? null : String.valueOf(resolved.value());
        } catch (RuntimeException ex) {
            throw new PaginationStrategyException("Failed to extract pagination token", ex);
        }
    }

    public static boolean reachedMaxPages(final int nextIteration, final int maxPages) {
        return nextIteration >= Math.max(1, maxPages);
    }
}
