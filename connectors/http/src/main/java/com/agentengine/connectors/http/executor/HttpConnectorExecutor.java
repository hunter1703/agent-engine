package com.agentengine.connectors.http.executor;

import com.agentengine.connectors.api.beans.ConnectorResult;
import com.agentengine.connectors.api.exceptions.ConnectorException;
import com.agentengine.connectors.http.TemplatedHttpConnectorSpec;
import com.agentengine.connectors.http.beans.HttpClientOptions;
import com.agentengine.connectors.http.beans.HttpConnectorSpec;
import com.agentengine.connectors.infra.ClientProvider;
import com.agentengine.connectors.infra.executor.ConnectorExecutor;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import okhttp3.*;
import okhttp3.internal.http.HttpMethod;

public class HttpConnectorExecutor
    implements ConnectorExecutor<Map<String, Object>, Map<String, Object>> {

  private final TemplatedHttpConnectorSpec templatedSpec;
  private final ClientProvider<HttpClientOptions, OkHttpClient> clientProvider;

  public HttpConnectorExecutor(
      TemplatedHttpConnectorSpec templatedSpec,
      ClientProvider<HttpClientOptions, OkHttpClient> clientProvider) {
    this.templatedSpec = templatedSpec;
    this.clientProvider = clientProvider;
  }

  @Override
  public ConnectorResult<Map<String, Object>> execute(Map<String, Object> input) {
    final HttpConnectorSpec evaluated = templatedSpec.evaluate(input);
    final OkHttpClient client = clientProvider.getClient(new HttpClientOptions());
    final HttpUrl url =
        buildUrl(evaluated.getBaseUrl(), evaluated.getPath(), evaluated.getQueryParams());
    final String method = evaluated.getMethod();

    Map<String, String> headers = evaluated.getHeaders();
    final RequestBody requestBody =
        createRequestBody(method, headers.get("Content-Type"), evaluated.getBody());

    final Request.Builder requestBuilder =
        new Request.Builder().url(url).method(method, requestBody);
    headers.forEach(requestBuilder::addHeader);

    try (Response response = client.newCall(requestBuilder.build()).execute()) {
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

  private static HttpUrl buildUrl(String baseUrl, String path, Map<String, String> queryParams) {
    if (!baseUrl.endsWith("/")) {
      baseUrl = baseUrl + "/";
    }
    if (path.startsWith("/")) {
      path = path.substring(1);
    }

    final HttpUrl parsed = HttpUrl.parse(baseUrl + path);

    final HttpUrl.Builder builder = Objects.requireNonNull(parsed).newBuilder();
    queryParams = CollectionUtils.nullSafeMap(queryParams);
    queryParams.forEach(builder::addQueryParameter);
    return builder.build();
  }

  private static RequestBody createRequestBody(
      final String method, final String mimeType, final Map<String, Object> body) {
    if (!HttpMethod.permitsRequestBody(method)) {
      return null;
    }

    MediaType mediaType = MediaType.parse(mimeType);
    final String subType = mediaType == null ? "" : mediaType.subtype();
    return switch (subType) {
      case "json" -> RequestBody.create(JsonUtils.toJson(body), mediaType);
      default -> RequestBody.create(JsonUtils.toJson(body), mediaType);
    };
  }

  private static List<Map<String, Object>> buildResult(
      final String mimeType, final byte[] responseBody) {
    if (mimeType == null) {
      return List.of(Map.of("response", responseBody));
    }

    final MediaType mediaType = MediaType.parse(mimeType);
    final String subType = mediaType == null ? null : mediaType.subtype();
    return switch (subType) {
      case "json" -> List.of(JsonUtils.fromJson(new String(responseBody), Map.class));
      default -> List.of(Map.of("raw", responseBody));
    };
  }
}
