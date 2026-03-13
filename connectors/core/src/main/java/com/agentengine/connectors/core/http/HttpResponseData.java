package com.agentengine.connectors.core.http;

import java.util.List;
import java.util.Map;

public record HttpResponseData(int statusCode, Map<String, List<String>> headers, String body) {

  public HttpResponseData {
    headers = headers == null ? Map.of() : Map.copyOf(headers);
    body = body == null ? "" : body;
  }

  public Map<String, String> flattenHeaders() {
    return headers.entrySet().stream().filter(entry -> !entry.getValue().isEmpty())
        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getFirst(), (left, right) -> left));
  }
}
