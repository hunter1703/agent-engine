package com.agentengine.util.vectordb;

import com.agentengine.util.common.CollectionUtils;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP-based Qdrant client using Java 11+ HttpClient.
 *
 * <p>Replaces the gRPC-based {@code io.qdrant:client} to avoid protobuf classpath conflicts.
 * Implements only the operations needed by {@link QdrantVectorStore}.
 */
public final class QdrantHttpClient {

  private static final Logger LOG = LoggerFactory.getLogger(QdrantHttpClient.class);

  private final String baseUrl;
  private final String apiKey;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public QdrantHttpClient(final String host, final int port, final String apiKey) {
    this.baseUrl = String.format("http://%s:%d", host, port);
    this.apiKey = apiKey;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    this.objectMapper =
        new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  }

  // ── Core Operations ───────────────────────────────────────────────────────

  public UpsertResponse upsert(final String collection, final UpsertRequest request) {
    final String url = String.format("%s/collections/%s/points", baseUrl, collection);
    return put(url, request, UpsertResponse.class);
  }

  public SearchResponse search(final String collection, final SearchRequest request) {
    final String url = String.format("%s/collections/%s/points/search", baseUrl, collection);
    return post(url, request, SearchResponse.class);
  }

  public RetrieveResponse retrieve(final String collection, final RetrieveRequest request) {
    final String url = String.format("%s/collections/%s/points", baseUrl, collection);
    return post(url, request, RetrieveResponse.class);
  }

  public QueryResponse query(final String collection, final QueryRequest request) {
    final String url = String.format("%s/collections/%s/points/query", baseUrl, collection);
    return post(url, request, QueryResponse.class);
  }

  public DeleteResponse delete(final String collection, final DeleteRequest request) {
    final String url = String.format("%s/collections/%s/points/delete", baseUrl, collection);

    LOG.info(
        "Qdrant delete request: collection={} points={} filter={}",
        collection,
        request.points(),
        request.filter());

    // Build the request body based on what's provided
    Object requestBody;
    final List<String> points = request.points();
    if (CollectionUtils.isNotEmpty(points)) {
      requestBody = Map.of("points", points);
      LOG.info("Qdrant delete by points: {}", points);
    } else {
      final Filter filter = request.filter();
      if (filter != null) {
        requestBody = Map.of("filter", filter);
        LOG.info("Qdrant delete by filter: must={} should={}", filter.must(), filter.should());
      } else {
        LOG.error("DeleteRequest has neither points nor filter");
        throw new IllegalArgumentException("DeleteRequest must have either points or filter");
      }
    }

    return post(url, requestBody, DeleteResponse.class);
  }

  // ── HTTP Helpers ──────────────────────────────────────────────────────────

  private <T> T put(final String url, final Object body, final Class<T> responseType) {
    return request("PUT", url, body, responseType);
  }

  private <T> T post(final String url, final Object body, final Class<T> responseType) {
    return request("POST", url, body, responseType);
  }

  private <T> T request(
      final String method, final String url, final Object body, final Class<T> responseType) {
    try {
      final String json = objectMapper.writeValueAsString(body);
      LOG.info("Qdrant HTTP {} to {} with body: {}", method, url, json);

      final HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("Content-Type", "application/json")
              .method(method, HttpRequest.BodyPublishers.ofString(json))
              .timeout(Duration.ofSeconds(30));

      if (apiKey != null && !apiKey.isBlank()) {
        builder.header("api-key", apiKey);
      }

      final HttpResponse<String> response =
          httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      final int status = response.statusCode();
      final String responseBody = response.body();

      LOG.info(
          "Qdrant HTTP {} to {} returned status={} body={}", method, url, status, responseBody);

      if (status < 200 || status >= 300) {
        LOG.error(
            "Qdrant HTTP error: status={} url={} requestBody={} responseBody={}",
            status,
            url,
            json,
            responseBody);
        throw new QdrantHttpException(
            String.format("Qdrant HTTP error: %d %s", status, responseBody));
      }

      return objectMapper.readValue(responseBody, responseType);
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      LOG.error("Qdrant HTTP request failed: url={} error={}", url, e.getMessage(), e);
      throw new QdrantHttpException("Qdrant HTTP request failed: " + e.getMessage(), e);
    }
  }

  // ── Request/Response DTOs ─────────────────────────────────────────────────

  public record UpsertRequest(List<Point> points) {}

  // Point can have either a flat vector array OR named vectors map, but not both
  public record Point(String id, Object vector, Map<String, Object> payload) {}

  public record SearchRequest(
      List<Float> vector,
      String vectorName,
      Integer limit,
      Float scoreThreshold,
      Filter filter,
      Boolean withPayload) {}

  public record QueryRequest(
      List<PrefetchQuery> prefetch,
      Object query,
      String using,
      Filter filter,
      Integer limit,
      Float scoreThreshold,
      Boolean withPayload) {}

  public record PrefetchQuery(List<Float> query, String using, Integer limit) {}

  public record RetrieveRequest(List<String> ids, Boolean withPayload) {}

  public record DeleteRequest(List<String> points, Filter filter) {}

  public record Filter(List<Condition> must, List<Condition> should) {}

  public record Condition(String key, MatchCondition match) {}

  public record MatchCondition(String value) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record UpsertResponse(String status, UpsertResult result) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record UpsertResult(Long operationId, String status) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record DeleteResponse(String status, DeleteResult result) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record DeleteResult(Long operationId, String status) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SearchResponse(List<ScoredPoint> result) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record QueryResponse(List<ScoredPoint> result) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ScoredPoint(String id, Float score, Map<String, Object> payload) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record RetrieveResponse(List<RetrievedPoint> result) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record RetrievedPoint(String id, Map<String, Object> payload) {}

  public static final class QdrantHttpException extends RuntimeException {
    public QdrantHttpException(final String message) {
      super(message);
    }

    public QdrantHttpException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}
