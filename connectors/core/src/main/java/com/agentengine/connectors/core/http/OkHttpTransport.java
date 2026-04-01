package com.agentengine.connectors.core.http;

import com.agentengine.connectors.core.config.HttpMethod;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Singleton
public final class OkHttpTransport implements HttpTransport {

    private static final MediaType DEFAULT_JSON_TYPE = MediaType.parse("application/json");

    private final OkHttpClient baseClient;

    public OkHttpTransport() {
        this(new OkHttpClient());
    }

    public OkHttpTransport(final OkHttpClient baseClient) {
        this.baseClient = baseClient;
    }

    @Override
    public HttpResponseData execute(final HttpRequestData requestData) {
        final HttpUrl resolvedUrl = buildUrl(requestData.url(), requestData.query());
        final Request.Builder requestBuilder = new Request.Builder().url(resolvedUrl);

        requestData.headers().forEach(requestBuilder::addHeader);

        final RequestBody requestBody = createRequestBody(requestData);
        requestBuilder.method(requestData.method().name(), requestBody);

        final OkHttpClient effectiveClient = createClient(requestData);

        try (Response response = effectiveClient.newCall(requestBuilder.build()).execute()) {
            final ResponseBody responseBody = response.body();
            final String body = responseBody == null ? "" : responseBody.string();
            return new HttpResponseData(
                    response.code(), toHeaderMap(response.headers().toMultimap()), body);
        } catch (IOException ex) {
            throw new HttpTransportException("HTTP request failed", ex);
        }
    }

    private OkHttpClient createClient(final HttpRequestData requestData) {
        OkHttpClient.Builder builder = baseClient.newBuilder();
        if (requestData.connectTimeoutMs() != null) {
            builder = builder.connectTimeout(Duration.ofMillis(requestData.connectTimeoutMs()));
        }
        if (requestData.readTimeoutMs() != null) {
            builder = builder.readTimeout(Duration.ofMillis(requestData.readTimeoutMs()));
        }
        if (requestData.writeTimeoutMs() != null) {
            builder = builder.writeTimeout(Duration.ofMillis(requestData.writeTimeoutMs()));
        }
        return builder.build();
    }

    private static HttpUrl buildUrl(final String url, final Map<String, String> query) {
        final HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null) {
            throw new HttpTransportException("Invalid URL: " + url);
        }

        final HttpUrl.Builder builder = parsed.newBuilder();
        query.forEach(builder::addQueryParameter);
        return builder.build();
    }

    private static RequestBody createRequestBody(final HttpRequestData requestData) {
        if (requestData.method() == HttpMethod.GET
                || requestData.method() == HttpMethod.DELETE
                || requestData.method() == HttpMethod.HEAD
                || requestData.method() == HttpMethod.OPTIONS) {
            return null;
        }

        final String body = requestData.body() == null ? "" : requestData.body();
        final MediaType mediaType =
                requestData.contentType() == null || requestData.contentType().isBlank()
                        ? DEFAULT_JSON_TYPE
                        : MediaType.parse(requestData.contentType());
        final MediaType effectiveMediaType = mediaType == null ? DEFAULT_JSON_TYPE : mediaType;
        return RequestBody.create(body, effectiveMediaType);
    }

    private static Map<String, List<String>> toHeaderMap(final Map<String, List<String>> headers) {
        return new LinkedHashMap<>(headers);
    }
}
