package com.agentengine.util.common.exception;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AssetNotFoundException extends RuntimeException implements ApplicationException {
  private final String assetType;
  private final String assetId;

  @JsonCreator
  public AssetNotFoundException(
      @JsonProperty("assetType") final String assetType,
      @JsonProperty("assetId") final String assetId) {
    super(buildMessage(assetType, assetId));
    this.assetType = assetType;
    this.assetId = assetId;
  }

  public AssetNotFoundException(
      final String assetType, final String assetId, final Throwable cause) {
    super(buildMessage(assetType, assetId), cause);
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
    return "Asset not found: type="
        + String.valueOf(assetType)
        + ", identifier="
        + String.valueOf(assetId);
  }
}
