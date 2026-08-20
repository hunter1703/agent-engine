package com.agentengine.connectors.infra.auth;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true)
public abstract class AuthDecoratorSpec {
  public enum Type {
    HEADER,
    UNKNOWN;

    public static Type valueOfOrUnknown(final String type) {
      try {
        return Type.valueOf(type);
      } catch (final IllegalArgumentException e) {
        return UNKNOWN;
      }
    }
  }

  private String type;

  public AuthDecoratorSpec() {}

  public AuthDecoratorSpec(AuthDecoratorSpec.Type type) {
    this.type = type.name();
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }
}
