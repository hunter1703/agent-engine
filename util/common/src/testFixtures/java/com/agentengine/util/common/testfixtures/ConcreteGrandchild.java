package com.agentengine.util.common.testfixtures;

/** Resolves {@code GenericMiddleChild<T>}'s {@code T} to a concrete type two levels up. */
public class ConcreteGrandchild extends GenericMiddleChild<Child1> {

  public ConcreteGrandchild() {}

  public ConcreteGrandchild(final Child1 value, final String middleTag) {
    super(value, middleTag);
  }
}
