package com.agentengine.connectors.core.response;

public interface ResponseExtractor {

  Object extract(String responseBody, String jsonPath);
}
