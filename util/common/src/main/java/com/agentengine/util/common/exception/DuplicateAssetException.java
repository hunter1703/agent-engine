package com.agentengine.util.common.exception;

public class DuplicateAssetException extends RuntimeException {
  private final String assetType;
  private final String assetId;

  public DuplicateAssetException(final String assetType, final String assetId) {
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
    return "Asset already exists: type=" + String.valueOf(assetType) + ", id=" + String.valueOf(assetId);
  }
}
