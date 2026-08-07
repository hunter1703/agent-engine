package com.agentengine.chaos.core.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.OptionalInt;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HttpRuntimeHealthClient implements RuntimeHealthClient {

    private static final Logger LOG = LoggerFactory.getLogger(HttpRuntimeHealthClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OkHttpClient httpClient;
    private final String activeSessionsUrl;

    public HttpRuntimeHealthClient(final OkHttpClient httpClient, final String activeSessionsUrl) {
        this.httpClient = httpClient;
        this.activeSessionsUrl = activeSessionsUrl;
    }

    @Override
    public OptionalInt activeSessionCount() {
        final Request request =
                new Request.Builder().url(activeSessionsUrl).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                LOG.warn("Runtime health endpoint returned status {}", response.code());
                return OptionalInt.empty();
            }
            final JsonNode root = MAPPER.readTree(response.body().string());
            final JsonNode activeSessions = root.path("activeSessions");
            return activeSessions.isInt() ? OptionalInt.of(activeSessions.asInt()) : OptionalInt.empty();
        } catch (IOException ex) {
            LOG.warn("Failed to query runtime health endpoint", ex);
            return OptionalInt.empty();
        }
    }
}
