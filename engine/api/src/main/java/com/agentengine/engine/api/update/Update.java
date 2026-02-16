package com.agentengine.engine.api.update;

import java.util.List;
import java.util.Objects;

public record Update(List<Operation> operations) {

  public Update {
    operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
  }

  public static Update of(final Operation... operations) {
    return new Update(List.of(operations));
  }
}
