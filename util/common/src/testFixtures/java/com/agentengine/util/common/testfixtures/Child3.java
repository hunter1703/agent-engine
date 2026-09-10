package com.agentengine.util.common.testfixtures;

/** Nests a non-final concrete type ({@link Child1}) as the type parameter. */
public class Child3 extends Parent<Child1> {

  public Child3() {}

  public Child3(final Child1 value) {
    super(value);
  }
}
