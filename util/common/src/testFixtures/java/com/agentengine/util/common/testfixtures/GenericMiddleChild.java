package com.agentengine.util.common.testfixtures;

import java.util.Objects;

/**
 * A generic class that extends a generic class ({@link Parent}) and stays generic itself —
 * neither level narrows {@code T} to a concrete type. See {@link ConcreteGrandchild} and {@link
 * StillGenericGrandchild} for how this resolves (or doesn't) further down the chain.
 */
public abstract class GenericMiddleChild<T> extends Parent<T> {

  private String middleTag;

  public GenericMiddleChild() {}

  public GenericMiddleChild(final T value, final String middleTag) {
    super(value);
    this.middleTag = middleTag;
  }

  public String getMiddleTag() {
    return middleTag;
  }

  public void setMiddleTag(final String middleTag) {
    this.middleTag = middleTag;
  }

  @Override
  public boolean equals(final Object other) {
    return super.equals(other)
        && other instanceof GenericMiddleChild<?> that
        && Objects.equals(middleTag, that.middleTag);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), middleTag);
  }
}
