package com.agentengine.util.common.testfixtures;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Objects;

/**
 * Test-only polymorphic hierarchy: explicit {@code @JsonTypeInfo(Id.CLASS)}, independent of any
 * default-typing mode. Generic so it can be nested at any depth (e.g. {@code Parent<InnerParent<String>>}).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
public class InnerParent<T> {

  private T value;

  public InnerParent() {}

  public InnerParent(final T value) {
    this.value = value;
  }

  public T getValue() {
    return value;
  }

  public void setValue(final T value) {
    this.value = value;
  }

  @Override
  public boolean equals(final Object other) {
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    final InnerParent<?> that = (InnerParent<?>) other;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
