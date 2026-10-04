package com.agentengine.util.common.exception;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class UnauthorizedException extends RuntimeException implements ApplicationException {
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

  @JsonCreator
  private UnauthorizedException(
      @JsonProperty("message") final String message,
      @JsonProperty("assetType") final String assetType,
      @JsonProperty("assetId") final String assetId) {
    super(message);
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
    return "Not authorized: type=" + assetType + ", identifier=" + assetId;
  }
}
