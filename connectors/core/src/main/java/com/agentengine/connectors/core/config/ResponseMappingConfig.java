package com.agentengine.connectors.core.config;

import java.util.List;

public record ResponseMappingConfig(String dataJsonPath, String metadataJsonPath, String errorCodeJsonPath, String errorMessageJsonPath,
    List<Integer> successStatusCodes, boolean includeRawBody) {

  public ResponseMappingConfig {
    dataJsonPath = dataJsonPath == null || dataJsonPath.isBlank() ? "$" : dataJsonPath;
    successStatusCodes = successStatusCodes == null ? List.of() : List.copyOf(successStatusCodes);
  }

  public boolean hasCustomSuccessStatusCodes() {
    return !successStatusCodes.isEmpty();
  }
}
