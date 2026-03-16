package com.agentengine.connectors.core.config;

import java.util.List;

public record ResponseMappingConfig(String output, String metadata, String errorCode, String errorMessage,
    List<Integer> successStatusCodes, boolean includeRawBody) {

  public ResponseMappingConfig {
    successStatusCodes = successStatusCodes == null ? List.of() : List.copyOf(successStatusCodes);
  }

  public boolean hasCustomSuccessStatusCodes() {
    return !successStatusCodes.isEmpty();
  }
}
