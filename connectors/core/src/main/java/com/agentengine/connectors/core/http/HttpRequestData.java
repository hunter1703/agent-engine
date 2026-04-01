package com.agentengine.connectors.core.http;

import com.agentengine.connectors.core.config.HttpMethod;
import java.util.Map;

public record HttpRequestData(
        HttpMethod method,
        String url,
        Map<String, String> headers,
        Map<String, String> query,
        String body,
        String contentType,
        Long connectTimeoutMs,
        Long readTimeoutMs,
        Long writeTimeoutMs) {

    public HttpRequestData {
        method = method == null ? HttpMethod.UNKNOWN : method;
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        query = query == null ? Map.of() : Map.copyOf(query);
    }
}
