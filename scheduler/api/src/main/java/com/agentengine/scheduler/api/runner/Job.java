package com.agentengine.scheduler.api.runner;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.ms.client.MicroServiceClientProvider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;

/**
 * A unit of scheduled work. Implementations are instantiated per run and must declare a public
 * constructor taking a {@link JobContext}.
 */
public abstract class Job {

  protected final JobContext context;

  protected Job(final JobContext context) {
    this.context = context;
  }

  /**
   * Runs the job. Never returns null; use {@link JobResult#empty()} when there is nothing to carry
   * forward.
   */
  public abstract JobResult run();

  /** Looks up a {@code @MicroService} client; implementations are instantiated by reflection. */
  protected final <T> T service(final Class<T> type) {
    try (InstanceHandle<MicroServiceClientProvider> handle =
        Arc.container().instance(MicroServiceClientProvider.class)) {
      return handle.get().get(type);
    }
  }

  protected final String requireField(final String key) {
    final String value = optionalField(key);
    if (value == null) {
      throw new IllegalArgumentException(
          "Missing required '" + key + "' payload field for " + getClass().getSimpleName());
    }
    return value;
  }

  protected final String optionalField(final String key) {
    return CollectionUtils.getStringValueFromMap(context.jobData(), key);
  }
}
