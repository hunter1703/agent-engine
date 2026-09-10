package com.agentengine.util.common.testfixtures;

import java.util.Objects;

/** Test-only polymorphic hierarchy: no annotation, relies on default typing to round-trip. */
public abstract class Parent<T> {

  private T value;

  public Parent() {}

  public Parent(final T value) {
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
    final Parent<?> that = (Parent<?>) other;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
