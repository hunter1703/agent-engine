package com.agentengine.engine.api.beans.session;

import java.util.Objects;

public record PlanItem(String id, String step, PlanStatus status) {

  public PlanItem {
    if (status == null) {
      status = PlanStatus.PENDING;
    }
  }

  public static PlanItem pending(final String step) {
    return new PlanItem(null, step, PlanStatus.PENDING);
  }

  public static PlanItem pending(final String id, final String step) {
    return new PlanItem(id, step, PlanStatus.PENDING);
  }

  public PlanItem withStatus(final PlanStatus status) {
    return new PlanItem(id, step, Objects.requireNonNullElse(status, PlanStatus.PENDING));
  }

  public PlanItem withId(final String id) {
    return new PlanItem(id, step, status);
  }
}
