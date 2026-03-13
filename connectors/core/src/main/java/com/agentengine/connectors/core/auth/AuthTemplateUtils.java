package com.agentengine.connectors.core.auth;

import com.agentengine.connectors.core.runtime.RequestContext;
import com.agentengine.connectors.core.template.ResolvedValue;
import com.agentengine.connectors.core.template.ResolvedValueStatus;
import com.agentengine.connectors.core.template.TemplateResolutionOptions;
import com.agentengine.connectors.core.template.TemplateResolver;

final class AuthTemplateUtils {

  private AuthTemplateUtils() {
  }

  static String resolveString(final String staticValue, final String templateValue, final RequestContext context,
      final TemplateResolver templateResolver, final boolean strictUnresolvedVariables, final String fallbackFromContextKey) {
    if (templateValue != null && !templateValue.isBlank()) {
      final ResolvedValue resolvedValue = templateResolver.resolve(templateValue, context,
          new TemplateResolutionOptions(strictUnresolvedVariables, false));
      if (resolvedValue.status() == ResolvedValueStatus.RESOLVED || resolvedValue.status() == ResolvedValueStatus.NULL_VALUE) {
        return resolvedValue.value() == null ? null : String.valueOf(resolvedValue.value());
      }
      return null;
    }

    if (staticValue != null && !staticValue.isBlank()) {
      return staticValue;
    }

    if (fallbackFromContextKey == null || context == null) {
      return null;
    }

    final Object contextValue = context.auth().get(fallbackFromContextKey);
    return contextValue == null ? null : String.valueOf(contextValue);
  }
}
