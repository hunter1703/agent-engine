package com.agentengine.chaos.core.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin client over the Prometheus HTTP API's instant-query endpoint. Returns {@code
 * Optional.empty()} rather than throwing when Prometheus is unreachable or a query yields no
 * series, so callers (steady-state baseline collection) can degrade to INCOMPLETE metrics instead
 * of aborting.
 */
public final class PrometheusClient {

    private static final Logger LOG = LoggerFactory.getLogger(PrometheusClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OkHttpClient httpClient;
    private final String baseUrl;

    public PrometheusClient(final OkHttpClient httpClient, final String baseUrl) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
    }

    public Optional<Double> queryScalar(final String promql) {
        final HttpUrl url = HttpUrl.parse(baseUrl + "/api/v1/query");
        if (url == null) {
            LOG.warn("Invalid Prometheus base URL: {}", baseUrl);
            return Optional.empty();
        }

        final Request request = new Request.Builder()
                .url(url.newBuilder().addQueryParameter("query", promql).build())
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                LOG.warn("Prometheus query failed with status {}: {}", response.code(), promql);
                return Optional.empty();
            }
            return extractFirstValue(MAPPER.readTree(response.body().string()));
        } catch (IOException ex) {
            LOG.warn("Prometheus query threw an exception: {}", promql, ex);
            return Optional.empty();
        }
    }

    private static Optional<Double> extractFirstValue(final JsonNode root) {
        final JsonNode result = root.path("data").path("result");
        if (!result.isArray() || result.isEmpty()) {
            return Optional.empty();
        }
        final JsonNode value = result.get(0).path("value");
        if (!value.isArray() || value.size() < 2) {
            return Optional.empty();
        }
        try {
            return Optional.of(Double.parseDouble(value.get(1).asText()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
