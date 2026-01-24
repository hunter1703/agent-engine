package com.agentengine.engine.api.beans.session;

import java.util.Objects;

public record PlanItem(String step, PlanStatus status) {

  public PlanItem {
    if (status == null) {
      status = PlanStatus.PENDING;
    }
  }

  public static PlanItem pending(final String step) {
    return new PlanItem(step, PlanStatus.PENDING);
  }

  public PlanItem withStatus(final PlanStatus status) {
    return new PlanItem(step, Objects.requireNonNullElse(status, PlanStatus.PENDING));
  }
}
