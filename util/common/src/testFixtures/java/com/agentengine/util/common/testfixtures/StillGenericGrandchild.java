package com.agentengine.util.common.testfixtures;

import java.util.Objects;

/** Extends a generic-extending-generic ({@link GenericMiddleChild}) and stays generic itself — a
 * third consecutive unresolved level. See {@link ConcreteGreatGrandchild} for where it finally
 * resolves. */
public class StillGenericGrandchild<T> extends GenericMiddleChild<T> {

  private String extra;

  public StillGenericGrandchild() {}

  public StillGenericGrandchild(final T value, final String middleTag, final String extra) {
    super(value, middleTag);
    this.extra = extra;
  }

  public String getExtra() {
    return extra;
  }

  public void setExtra(final String extra) {
    this.extra = extra;
  }

  @Override
  public boolean equals(final Object other) {
    return super.equals(other)
        && other instanceof StillGenericGrandchild<?> that
        && Objects.equals(extra, that.extra);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), extra);
  }
}
