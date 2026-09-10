package com.agentengine.util.common.testfixtures;

/**
 * A subclass of an already-concrete class, with no {@code @JsonTypeInfo} of its own — proves that a
 * declared-{@link Child1} slot does NOT recover a runtime subclass under {@code JAVA_LANG_OBJECT}
 * (the declared type isn't {@code Object} or abstract). A concrete class used as an extension point
 * must self-describe at the class level; no default-typing mode covers this case for free.
 */
public class Child1Variant extends Child1 {

  private String extra;

  public Child1Variant() {}

  public Child1Variant(final String value, final String extra) {
    super(value);
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
        && other instanceof Child1Variant that
        && java.util.Objects.equals(extra, that.extra);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(super.hashCode(), extra);
  }
}
