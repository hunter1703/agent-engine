package com.agentengine.connectors.core.response;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public final class JsonPathResponseExtractor implements ResponseExtractor {

  @Inject
  public JsonPathResponseExtractor() {
  }

  @Override
  public Object extract(final String responseBody, final String jsonPath) {
    if (jsonPath == null || jsonPath.isBlank()) {
      return responseBody;
    }
    if (responseBody == null || responseBody.isBlank()) {
      return null;
    }
    try {
      return JsonPath.read(responseBody, jsonPath);
    } catch (PathNotFoundException ex) {
      return null;
    }
  }
}
