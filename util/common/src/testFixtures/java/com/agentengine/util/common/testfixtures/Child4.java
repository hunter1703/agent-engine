package com.agentengine.util.common.testfixtures;

/** Nests an explicitly-annotated generic type ({@link InnerParent}) as the type parameter. */
public class Child4 extends Parent<InnerParent<String>> {

  public Child4() {}

  public Child4(final InnerParent<String> value) {
    super(value);
  }
}
