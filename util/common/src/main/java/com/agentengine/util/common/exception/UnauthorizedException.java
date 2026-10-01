package com.agentengine.util.common.exception;

public class UnauthorizedException extends RuntimeException {
  private final String assetType;
  private final String assetId;

  public UnauthorizedException(final String assetType, final String assetId) {
    super(buildMessage(assetType, assetId));
    this.assetType = assetType;
    this.assetId = assetId;
  }

  public UnauthorizedException(final String message) {
    super(message);
    this.assetType = null;
    this.assetId = null;
  }

  public String getAssetType() {
    return assetType;
  }

  public String getAssetId() {
    return assetId;
  }

  private static String buildMessage(final String assetType, final String assetId) {
    return "Not authorized: type=" + assetType + ", identifier=" + assetId;
  }
}
