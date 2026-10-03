package com.agentengine.util.common.exception;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DuplicateAssetException extends RuntimeException implements ApplicationException {
  private final String assetType;
  private final String assetId;

  @JsonCreator
  public DuplicateAssetException(
      @JsonProperty("assetType") final String assetType,
      @JsonProperty("assetId") final String assetId) {
    super(buildMessage(assetType, assetId));
    this.assetType = assetType;
    this.assetId = assetId;
  }

  public String getAssetType() {
    return assetType;
  }

  public String getAssetId() {
    return assetId;
  }

  private static String buildMessage(final String assetType, final String assetId) {
    return "Asset already exists: type="
        + String.valueOf(assetType)
        + ", id="
        + String.valueOf(assetId);
  }
}
