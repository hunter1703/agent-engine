package com.agentengine.connectors.core.template;

import com.agentengine.connectors.core.runtime.RequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public final class DefaultTemplateResolver implements TemplateResolver {

  private static final Pattern FULL_DOLLAR_EXPRESSION =
      Pattern.compile("^\\s*\\$\\{([^}]+)}\\s*$", Pattern.DOTALL);
  private static final Pattern FULL_CURLY_EXPRESSION =
      Pattern.compile("^\\s*\\{\\{\\s*(.+?)\\s*}}\\s*$", Pattern.DOTALL);
  private static final Pattern DOLLAR_EXPRESSION = Pattern.compile("\\$\\{([^}]+)}");
  private static final Pattern CURLY_EXPRESSION = Pattern.compile("\\{\\{\\s*(.+?)\\s*}}");

  private final GroovySandboxEvaluator evaluator;

  @Inject
  public DefaultTemplateResolver(final GroovySandboxEvaluator evaluator) {
    this.evaluator = evaluator;
  }

  @Override
  public ResolvedValue resolve(
      final Object template,
      final RequestContext context,
      final TemplateResolutionOptions options) {
    final Map<String, Object> variables = context == null ? Map.of() : context.toTemplateVariables();
    return resolveInternal(template, variables, options == null ? TemplateResolutionOptions.strict() : options);
  }

  @SuppressWarnings("unchecked")
  private ResolvedValue resolveInternal(
      final Object template,
      final Map<String, Object> variables,
      final TemplateResolutionOptions options) {
    if (template == null) {
      return ResolvedValue.nullValue();
    }

    if (template instanceof String stringTemplate) {
      return resolveString(stringTemplate, variables, options);
    }

    if (template instanceof Number || template instanceof Boolean) {
      return ResolvedValue.resolved(template);
    }

    if (template instanceof Map<?, ?> mapTemplate) {
      if (isDirectiveObject(mapTemplate)) {
        return resolveDirective((Map<String, Object>) mapTemplate, variables, options);
      }
      final Map<String, Object> resolvedMap = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : mapTemplate.entrySet()) {
        final String key = String.valueOf(entry.getKey());
        final ResolvedValue resolvedEntry = resolveInternal(entry.getValue(), variables, options);
        if (resolvedEntry.status() == ResolvedValueStatus.OMITTED) {
          continue;
        }
        if (resolvedEntry.status() == ResolvedValueStatus.UNRESOLVED) {
          if (options.strictUnresolvedVariables()) {
            throw new TemplateResolutionException("Failed to resolve template key: " + key);
          }
          return ResolvedValue.unresolved();
        }
        if (resolvedEntry.status() == ResolvedValueStatus.NULL_VALUE && options.omitNulls()) {
          continue;
        }
        resolvedMap.put(key, resolvedEntry.value());
      }
      return ResolvedValue.resolved(resolvedMap);
    }

    if (template instanceof Iterable<?> iterableTemplate) {
      final List<Object> resolvedList = new ArrayList<>();
      for (Object item : iterableTemplate) {
        final ResolvedValue resolvedItem = resolveInternal(item, variables, options);
        if (resolvedItem.status() == ResolvedValueStatus.OMITTED) {
          continue;
        }
        if (resolvedItem.status() == ResolvedValueStatus.UNRESOLVED) {
          if (options.strictUnresolvedVariables()) {
            throw new TemplateResolutionException("Failed to resolve list template value");
          }
          return ResolvedValue.unresolved();
        }
        if (resolvedItem.status() == ResolvedValueStatus.NULL_VALUE && options.omitNulls()) {
          continue;
        }
        resolvedList.add(resolvedItem.value());
      }
      return ResolvedValue.resolved(resolvedList);
    }

    return ResolvedValue.resolved(template);
  }

  private ResolvedValue resolveDirective(
      final Map<String, Object> directiveTemplate,
      final Map<String, Object> variables,
      final TemplateResolutionOptions options) {
    if (directiveTemplate.containsKey(TemplateDirective.INCLUDE_IF.key())) {
      final Object includeValue = directiveTemplate.get(TemplateDirective.INCLUDE_IF.key());
      final ResolvedValue includeResolved =
          includeValue instanceof String includeExpression
              ? resolveExpression(includeExpression, variables, options)
              : resolveInternal(includeValue, variables, options);
      if (!toBoolean(includeResolved.value())) {
        return ResolvedValue.omitted();
      }
    }

    final boolean optional =
        directiveTemplate.containsKey(TemplateDirective.OPTIONAL.key())
            && toBoolean(directiveTemplate.get(TemplateDirective.OPTIONAL.key()));

    final Object expressionValue = directiveTemplate.get(TemplateDirective.EXPR.key());
    final Object templateValue = directiveTemplate.get(TemplateDirective.TEMPLATE.key());

    final ResolvedValue resolved;
    final TemplateResolutionOptions effectiveOptions =
        optional ? new TemplateResolutionOptions(false, options.omitNulls()) : options;
    if (expressionValue != null) {
      resolved = resolveExpression(String.valueOf(expressionValue), variables, effectiveOptions);
    } else if (templateValue != null) {
      resolved = resolveInternal(templateValue, variables, effectiveOptions);
    } else {
      resolved = ResolvedValue.resolved(directiveTemplate);
    }

    if (optional
        && (resolved.status() == ResolvedValueStatus.UNRESOLVED
            || resolved.status() == ResolvedValueStatus.NULL_VALUE)) {
      return ResolvedValue.omitted();
    }

    return resolved;
  }

  private ResolvedValue resolveString(
      final String template,
      final Map<String, Object> variables,
      final TemplateResolutionOptions options) {
    final Matcher fullDollarMatcher = FULL_DOLLAR_EXPRESSION.matcher(template);
    if (fullDollarMatcher.matches()) {
      return resolveExpression(fullDollarMatcher.group(1), variables, options);
    }

    final Matcher fullCurlyMatcher = FULL_CURLY_EXPRESSION.matcher(template);
    if (fullCurlyMatcher.matches()) {
      return resolveExpression(fullCurlyMatcher.group(1), variables, options);
    }

    final ResolvedString curlyResolved = replaceInlineExpressions(template, CURLY_EXPRESSION, variables, options);
    if (curlyResolved.status() == ResolvedValueStatus.UNRESOLVED) {
      return ResolvedValue.unresolved();
    }

    final ResolvedString dollarResolved =
        replaceInlineExpressions(curlyResolved.value(), DOLLAR_EXPRESSION, variables, options);
    if (dollarResolved.status() == ResolvedValueStatus.UNRESOLVED) {
      return ResolvedValue.unresolved();
    }

    return ResolvedValue.resolved(dollarResolved.value());
  }

  private ResolvedString replaceInlineExpressions(
      final String template,
      final Pattern expressionPattern,
      final Map<String, Object> variables,
      final TemplateResolutionOptions options) {
    final Matcher matcher = expressionPattern.matcher(template);
    final StringBuilder builder = new StringBuilder();
    int cursor = 0;
    while (matcher.find()) {
      builder.append(template, cursor, matcher.start());
      final String expression = matcher.group(1);
      final ResolvedValue resolvedExpression = resolveExpression(expression, variables, options);
      if (resolvedExpression.status() == ResolvedValueStatus.UNRESOLVED) {
        return new ResolvedString(ResolvedValueStatus.UNRESOLVED, "");
      }
      if (resolvedExpression.status() != ResolvedValueStatus.NULL_VALUE) {
        builder.append(String.valueOf(resolvedExpression.value()));
      }
      cursor = matcher.end();
    }
    builder.append(template.substring(cursor));
    return new ResolvedString(ResolvedValueStatus.RESOLVED, builder.toString());
  }

  private ResolvedValue resolveExpression(
      final String expression,
      final Map<String, Object> variables,
      final TemplateResolutionOptions options) {
    try {
      final Object value = evaluator.evaluate(expression, variables);
      return value == null ? ResolvedValue.nullValue() : ResolvedValue.resolved(value);
    } catch (UnresolvedVariableException ex) {
      if (options.strictUnresolvedVariables()) {
        throw new TemplateResolutionException("Unresolved variable in expression: " + expression, ex);
      }
      return ResolvedValue.unresolved();
    }
  }

  private static boolean isDirectiveObject(final Map<?, ?> mapTemplate) {
    return mapTemplate.keySet().stream()
        .map(String::valueOf)
        .map(TemplateDirective::fromKey)
        .anyMatch(directive -> directive != TemplateDirective.UNKNOWN);
  }

  private static boolean toBoolean(final Object value) {
    if (value == null) {
      return false;
    }
    if (value instanceof Boolean booleanValue) {
      return booleanValue;
    }
    if (value instanceof Number numberValue) {
      return numberValue.intValue() != 0;
    }
    return "true".equalsIgnoreCase(String.valueOf(value).trim());
  }

  private record ResolvedString(ResolvedValueStatus status, String value) {}
}
