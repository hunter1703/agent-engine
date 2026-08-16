package com.agentengine.chaos.core.metrics;

import java.util.OptionalInt;

/** Queries the runtime service's admin/health surface for state Prometheus doesn't expose. */
public interface RuntimeHealthClient {

  OptionalInt activeSessionCount();
}
