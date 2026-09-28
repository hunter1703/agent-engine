package com.agentengine.util.agents.beans.config;

import com.agentengine.util.agents.builder.annotations.UiField;
import com.agentengine.util.agents.builder.annotations.UiText;
import jakarta.validation.constraints.NotBlank;
import java.util.Objects;

public class KeyValuePair {

  @UiField(label = "Key", order = 10)
  @UiText
  @NotBlank
  private String key;

  @UiField(label = "Value", order = 20)
  @UiText
  private Object value;

  public KeyValuePair() {}

  public KeyValuePair(final String key, final Object value) {
    this.key = key;
    this.value = value;
  }

  public String getKey() {
    return key;
  }

  public void setKey(final String key) {
    this.key = key;
  }

  public Object getValue() {
    return value;
  }

  public void setValue(final Object value) {
    this.value = value;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof KeyValuePair other)) {
      return false;
    }
    return Objects.equals(key, other.key) && Objects.equals(value, other.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, value);
  }
}
