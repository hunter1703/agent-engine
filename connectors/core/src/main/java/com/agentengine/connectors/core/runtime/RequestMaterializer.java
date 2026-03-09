package com.agentengine.connectors.core.runtime;

import com.agentengine.connectors.core.auth.AuthStrategyRegistry;
import com.agentengine.connectors.core.config.BodyType;
import com.agentengine.connectors.core.config.ConnectorDefinition;
import com.agentengine.connectors.core.config.EndpointConfig;
import com.agentengine.connectors.core.http.HttpRequestData;
import com.agentengine.connectors.core.pagination.PaginationDirective;
import com.agentengine.connectors.core.template.ResolvedValue;
import com.agentengine.connectors.core.template.ResolvedValueStatus;
import com.agentengine.connectors.core.template.TemplateResolutionOptions;
import com.agentengine.connectors.core.template.TemplateResolver;
import com.agentengine.util.JsonUtils;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public final class RequestMaterializer {

  private static final String JSON_CONTENT_TYPE = "application/json";
  private static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";
  private static final String TEXT_CONTENT_TYPE = "text/plain";

  private final TemplateResolver templateResolver;
  private final AuthStrategyRegistry authRegistry;

  @Inject
  public RequestMaterializer(
      final TemplateResolver templateResolver,
      final AuthStrategyRegistry authStrategyRegistry) {
    this.templateResolver = templateResolver;
    this.authRegistry = authStrategyRegistry;
  }

  public HttpRequestData materialize(
      final ConnectorDefinition definition,
      final RequestContext context,
      final PaginationDirective paginationDirective) {
    final EndpointConfig endpoint = definition.endpoint();
    final boolean strictUnresolved = definition.strictUnresolvedVariables();

    final String baseUrl =
        resolveStringValue(endpoint.baseUrl(), endpoint.baseUrlTemplate(), context, strictUnresolved);
    final String resolvedPath =
        resolveStringValue(endpoint.path(), endpoint.pathTemplate(), context, strictUnresolved);

    String resolvedUrl = joinUrl(baseUrl, resolvedPath);
    if (paginationDirective.overrideUrl() != null && !paginationDirective.overrideUrl().isBlank()) {
      resolvedUrl = paginationDirective.overrideUrl();
    }

    final Map<String, String> headers =
        resolveMap(definition.headers(), context, strictUnresolved, endpoint.omitNullHeaders());
    final Map<String, String> query =
        resolveMap(definition.query(), context, strictUnresolved, endpoint.omitNullQuery());
    query.putAll(paginationDirective.queryOverrides());

    final String body = resolveBody(definition, context);
    final String contentType = resolveContentType(definition);

    authRegistry.apply(
        definition.auth(), context, templateResolver, strictUnresolved, headers, query);

    return new HttpRequestData(
        endpoint.method(),
        resolvedUrl,
        headers,
        query,
        body,
        contentType,
        endpoint.connectTimeoutMs(),
        endpoint.readTimeoutMs(),
        endpoint.writeTimeoutMs());
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> resolveMap(
      final Map<String, Object> mapTemplate,
      final RequestContext context,
      final boolean strictUnresolved,
      final boolean omitNulls) {
    final ResolvedValue resolvedValue =
        templateResolver.resolve(
            mapTemplate,
            context,
            new TemplateResolutionOptions(strictUnresolved, omitNulls));

    if (resolvedValue.status() == ResolvedValueStatus.NULL_VALUE || resolvedValue.value() == null) {
      return new LinkedHashMap<>();
    }

    if (!(resolvedValue.value() instanceof Map<?, ?> resolvedMap)) {
      throw new ConnectorExecutionException("Expected map template to resolve into a map value");
    }

    final Map<String, String> output = new LinkedHashMap<>();
    resolvedMap.forEach(
        (key, value) -> {
          if (value == null && omitNulls) {
            return;
          }
          output.put(String.valueOf(key), value == null ? null : String.valueOf(value));
        });
    return output;
  }

  private String resolveBody(final ConnectorDefinition definition, final RequestContext context) {
    if (definition.body() == null || definition.body().template() == null) {
      return null;
    }

    final ResolvedValue resolvedBody =
        templateResolver.resolve(
            definition.body().template(),
            context,
            new TemplateResolutionOptions(
                definition.strictUnresolvedVariables(), definition.body().omitNulls()));

    if (resolvedBody.status() == ResolvedValueStatus.NULL_VALUE) {
      return null;
    }

    final Object value = resolvedBody.value();
    final BodyType bodyType = definition.body().type();

    return switch (bodyType) {
      case JSON -> writeJson(value);
      case FORM_URLENCODED -> toFormEncoded(value);
      case TEXT -> value == null ? null : String.valueOf(value);
      case UNKNOWN -> throw new ConnectorExecutionException("Body type is required when body is present");
    };
  }

  private static String resolveContentType(final ConnectorDefinition definition) {
    if (definition.body() == null || definition.body().template() == null) {
      return null;
    }
    if (definition.body().contentType() != null && !definition.body().contentType().isBlank()) {
      return definition.body().contentType();
    }
    return switch (definition.body().type()) {
      case JSON -> JSON_CONTENT_TYPE;
      case FORM_URLENCODED -> FORM_CONTENT_TYPE;
      case TEXT -> TEXT_CONTENT_TYPE;
      case UNKNOWN -> null;
    };
  }

  @SuppressWarnings("unchecked")
  private static String toFormEncoded(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof Map<?, ?> mapValue)) {
      throw new ConnectorExecutionException("FORM_URLENCODED body must resolve to a map");
    }

    final StringBuilder builder = new StringBuilder();
    boolean first = true;
    for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
      if (!first) {
        builder.append('&');
      }
      first = false;
      builder
          .append(URLEncoder.encode(String.valueOf(entry.getKey()), StandardCharsets.UTF_8))
          .append('=')
          .append(
              URLEncoder.encode(
                  entry.getValue() == null ? "" : String.valueOf(entry.getValue()),
                  StandardCharsets.UTF_8));
    }
    return builder.toString();
  }

  private String writeJson(final Object value) {
    return JsonUtils.toJson(value);
  }

  private String resolveStringValue(
      final String staticValue,
      final String templateValue,
      final RequestContext context,
      final boolean strictUnresolved) {
    if (templateValue != null && !templateValue.isBlank()) {
      final ResolvedValue resolvedValue =
          templateResolver.resolve(
              templateValue,
              context,
              new TemplateResolutionOptions(strictUnresolved, false));
      return resolvedValue.value() == null ? null : String.valueOf(resolvedValue.value());
    }
    return staticValue;
  }

  private static String joinUrl(final String baseUrl, final String path) {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new ConnectorExecutionException("Resolved base URL cannot be empty");
    }

    if (path == null || path.isBlank()) {
      return baseUrl;
    }

    final boolean baseEndsWithSlash = baseUrl.endsWith("/");
    final boolean pathStartsWithSlash = path.startsWith("/");

    if (baseEndsWithSlash && pathStartsWithSlash) {
      return baseUrl + path.substring(1);
    }
    if (!baseEndsWithSlash && !pathStartsWithSlash) {
      return baseUrl + "/" + path;
    }
    return baseUrl + path;
  }
}
