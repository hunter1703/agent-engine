package com.agentengine.util.context;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Who a request acts as: the system, nobody yet (before logging in), or a {@link UserCaller}.
 * Written as a single string — {@value #SYSTEM_NAME}, {@value #ANONYMOUS_NAME}, or the user
 * caller's own written form, which always starts with a user segment.
 */
public sealed interface Caller permits Caller.SystemCaller, Caller.AnonymousCaller, UserCaller {

  String SYSTEM_NAME = "System";
  String ANONYMOUS_NAME = "Anonymous";

  SystemCaller SYSTEM = new SystemCaller();
  AnonymousCaller ANONYMOUS = new AnonymousCaller();

  @JsonCreator
  static Caller parse(final String value) {
    return switch (value) {
      case SYSTEM_NAME -> SYSTEM;
      case ANONYMOUS_NAME -> ANONYMOUS;
      default -> UserCaller.parse(value);
    };
  }

  /** Acts with every permission, within its context's customer. */
  record SystemCaller() implements Caller {
    @JsonValue
    @Override
    public String toString() {
      return SYSTEM_NAME;
    }
  }

  /** Has not logged in, and may only do what is open to everyone. */
  record AnonymousCaller() implements Caller {
    @JsonValue
    @Override
    public String toString() {
      return ANONYMOUS_NAME;
    }
  }
}
