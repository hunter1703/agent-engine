package com.agentengine.util.common.context;

import java.util.Objects;

/**
 * The identity behind a {@link Context}: which customer (tenant) issued the request, and which of
 * that customer's users made it. Nested inside {@link Context} rather than flattened into it, per
 * that type's own guidance, so a future identity field never means touching every place that only
 * cares about {@code requestId}.
 */
public record UserContext(String customerId, String userId) {

  public UserContext {
    Objects.requireNonNull(customerId, "customerId");
    Objects.requireNonNull(userId, "userId");
  }
}
