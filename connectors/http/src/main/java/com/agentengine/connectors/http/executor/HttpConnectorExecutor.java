package com.agentengine.connectors.http.executor;

import com.agentengine.connectors.api.beans.ConnectorResult;
import com.agentengine.connectors.api.exceptions.ConnectorException;
import com.agentengine.connectors.http.TemplatedHttpExecutorSpec;
import com.agentengine.connectors.http.beans.HttpClientOptions;
import com.agentengine.connectors.http.beans.HttpExecutorSpec;
import com.agentengine.connectors.http.beans.HttpRequest;
import com.agentengine.connectors.infra.ClientProvider;
import com.agentengine.connectors.infra.auth.AuthDecorator;
import com.agentengine.connectors.infra.executor.ConnectorExecutor;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import okhttp3.*;
import okhttp3.internal.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpConnectorExecutor
    implements ConnectorExecutor<Map<String, Object>, Map<String, Object>> {

  private static final Logger LOG = LoggerFactory.getLogger(HttpConnectorExecutor.class);

  private final TemplatedHttpExecutorSpec templatedSpec;
  private final ClientProvider<HttpClientOptions, OkHttpClient> clientProvider;
  private final AuthDecorator<HttpRequest> authDecorator;

  public HttpConnectorExecutor(
      TemplatedHttpExecutorSpec templatedSpec,
      ClientProvider<HttpClientOptions, OkHttpClient> clientProvider,
      AuthDecorator<HttpRequest> authDecorator) {
    this.templatedSpec = templatedSpec;
    this.clientProvider = clientProvider;
    this.authDecorator = authDecorator;
  }

  @Override
  @SuppressWarnings("unchecked")
  public ConnectorResult<Map<String, Object>> execute(Map<String, Object> input) {
    final HttpExecutorSpec evaluated = templatedSpec.evaluate(input);
    final OkHttpClient client = clientProvider.getClient(new HttpClientOptions());
    final String method = evaluated.getMethod();

    final HttpRequest request =
        new HttpRequest(
            evaluated.getUrl(),
            (Map<String, Object>) evaluated.getQueryParams(),
            method,
            (Map<String, Object>) evaluated.getBody(),
            (Map<String, String>) evaluated.getHeaders());
    authDecorator.decorate(request);

    final Map<String, String> headers = request.getHeaders();
    final RequestBody requestBody =
        createRequestBody(method, headers.get("Content-Type"), request.getBody());
    final Request.Builder requestBuilder =
        new Request.Builder().url(request.getFullUrl()).method(method, requestBody);
    headers.forEach(requestBuilder::addHeader);

    LOG.info(
        "Sending {} {} headers={} body={}",
        method,
        request.getFullUrl(),
        headers,
        request.getBody());

    try (Response response = client.newCall(requestBuilder.build()).execute()) {
      LOG.info("Received {} {} -> {}", method, request.getFullUrl(), response.code());
      if (!response.isSuccessful()) {
        throw new ConnectorException("HTTP request failed with status code: " + response.code());
      }
      final ResponseBody responseBody = response.body();

      if (responseBody == null) {
        return new ConnectorResult<>(List.of(Map.of()));
      }
      final List<String> contentTypeHeaders =
          CollectionUtils.nullSafeList(response.headers("Content-Type"));
      final String mimeType =
          contentTypeHeaders.stream()
              .filter(contentType -> contentType.contains("application/"))
              .findFirst()
              .orElse(null);
      return new ConnectorResult<>(buildResult(mimeType, responseBody.bytes()));
    } catch (IOException ex) {
      throw new ConnectorException("HTTP request failed", ex);
    }
  }

  private static RequestBody createRequestBody(
      final String method, final String mimeType, final Map<String, Object> body) {
    if (!HttpMethod.permitsRequestBody(method)) {
      return null;
    }

    final MediaType mediaType = MediaType.parse(mimeType);
    final String subType = mediaType == null ? "" : mediaType.subtype();
    return switch (subType) {
      case "x-www-form-urlencoded" -> createFormBody(body);
      default -> RequestBody.create(JsonUtils.toJson(body), mediaType);
    };
  }

  private static RequestBody createFormBody(final Map<String, Object> body) {
    final FormBody.Builder builder = new FormBody.Builder();
    CollectionUtils.nullSafeMap(body)
        .forEach(
            (key, value) -> {
              if (value != null) {
                builder.add(key, String.valueOf(value));
              }
            });
    return builder.build();
  }

  private static List<Map<String, Object>> buildResult(
      final String mimeType, final byte[] responseBody) {
    if (mimeType == null) {
      return List.of(Map.of("response", responseBody));
    }

    final MediaType mediaType = MediaType.parse(mimeType);
    final String subType = mediaType == null ? null : mediaType.subtype();
    return switch (subType) {
      case "json" -> //noinspection unchecked
          List.of(JsonUtils.fromJson(new String(responseBody), Map.class));
      default -> List.of(Map.of("raw", responseBody));
    };
  }
}
