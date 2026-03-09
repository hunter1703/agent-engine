package com.agentengine.connectors.core.config;

public record BodyConfig(BodyType type, Object template, String contentType, boolean omitNulls) {

  public BodyConfig {
    type = type == null ? BodyType.UNKNOWN : type;
    omitNulls = omitNulls;
  }
}
