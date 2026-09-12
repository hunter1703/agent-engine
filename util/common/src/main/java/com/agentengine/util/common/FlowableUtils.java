package com.agentengine.util.common;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class FlowableUtils {

  private static final Scheduler STREAMING_SCHEDULER =
      Schedulers.from(ThreadUtils.newFixedThreadExecutor("streaming-batch-", 4));

  private FlowableUtils() {}

  public static Scheduler streamingScheduler() {
    return STREAMING_SCHEDULER;
  }

  public static <T> Flowable<T> withScheduled(
      final Flowable<T> source, final long intervalMillis, final Supplier<T> scheduledProducer) {
    if (source == null) {
      throw new IllegalArgumentException("Source Flowable cannot be null");
    }
    if (scheduledProducer == null) {
      return source;
    }
    if (intervalMillis <= 0) {
      throw new IllegalArgumentException("Interval must be strictly positive");
    }

    return source.publish(
        shared ->
            Flowable.merge(
                shared,
                Flowable.interval(intervalMillis, TimeUnit.MILLISECONDS, STREAMING_SCHEDULER)
                    .map(_ -> scheduledProducer.get())
                    .takeUntil(shared.ignoreElements().toFlowable())));
  }
}
