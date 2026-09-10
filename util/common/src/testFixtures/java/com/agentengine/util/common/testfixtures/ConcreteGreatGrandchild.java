package com.agentengine.util.common.testfixtures;

/**
 * The bottom of a four-level unannotated generic chain: {@link Parent}&lt;T&gt; -&gt; {@link
 * GenericMiddleChild}&lt;T&gt; -&gt; {@link StillGenericGrandchild}&lt;T&gt; -&gt; this (resolves
 * T to {@link Child3}, itself generic-wrapping-a-concrete-class). Nothing in this chain carries
 * {@code @JsonTypeInfo} — every level depends entirely on default typing to round-trip.
 */
public class ConcreteGreatGrandchild extends StillGenericGrandchild<Child3> {

  public ConcreteGreatGrandchild() {}

  public ConcreteGreatGrandchild(final Child3 value, final String middleTag, final String extra) {
    super(value, middleTag, extra);
  }
}
