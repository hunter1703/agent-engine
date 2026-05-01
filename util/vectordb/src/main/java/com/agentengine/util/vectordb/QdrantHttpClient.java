package com.agentengine.util.vectordb;

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
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    // ── Core Operations ───────────────────────────────────────────────────────

    public UpsertResponse upsert(final String collection, final UpsertRequest request) {
        final String url = String.format("%s/collections/%s/points", baseUrl, collection);
        return post(url, request, UpsertResponse.class);
    }

    public SearchResponse search(final String collection, final SearchRequest request) {
        final String url = String.format("%s/collections/%s/points/search", baseUrl, collection);
        return post(url, request, SearchResponse.class);
    }

    public RetrieveResponse retrieve(final String collection, final RetrieveRequest request) {
        final String url = String.format("%s/collections/%s/points", baseUrl, collection);
        return post(url, request, RetrieveResponse.class);
    }

    public DeleteResponse delete(final String collection, final DeleteRequest request) {
        final String url = String.format("%s/collections/%s/points/delete", baseUrl, collection);
        return post(url, request, DeleteResponse.class);
    }

    // ── HTTP Helpers ──────────────────────────────────────────────────────────

    private <T> T post(final String url, final Object body, final Class<T> responseType) {
        try {
            final String json = objectMapper.writeValueAsString(body);
            LOG.debug("POST {} body={}", url, json);

            final HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(30));

            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("api-key", apiKey);
            }

            final HttpResponse<String> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            final int status = response.statusCode();
            final String responseBody = response.body();

            LOG.debug("POST {} status={} body={}", url, status, responseBody);

            if (status < 200 || status >= 300) {
                throw new QdrantHttpException(String.format("Qdrant HTTP error: %d %s", status, responseBody));
            }

            return objectMapper.readValue(responseBody, responseType);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new QdrantHttpException("Qdrant HTTP request failed: " + e.getMessage(), e);
        }
    }

    // ── Request/Response DTOs ─────────────────────────────────────────────────

    public record UpsertRequest(List<Point> points) {}

    public record Point(String id, Map<String, List<Float>> vector, Map<String, Object> payload) {}

    public record SearchRequest(
            List<Float> vector,
            String vectorName,
            Integer limit,
            Float scoreThreshold,
            Filter filter,
            Boolean withPayload) {}

    public record RetrieveRequest(List<String> ids, Boolean withPayload) {}

    public record DeleteRequest(List<String> points, Filter filter) {}

    public record Filter(List<Condition> must, List<Condition> should) {}

    public record Condition(Match match, Filter filter) {}

    public record Match(String key, MatchValue value) {}

    public record MatchValue(String keyword) {}

    public record UpsertResponse(String status, String result) {}

    public record DeleteResponse(String status) {}

    public record SearchResponse(List<ScoredPoint> result) {}

    public record ScoredPoint(String id, Float score, Map<String, Object> payload) {}

    public record RetrieveResponse(List<RetrievedPoint> result) {}

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
