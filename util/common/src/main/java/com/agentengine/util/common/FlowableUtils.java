package com.agentengine.util.common;

import io.reactivex.rxjava3.core.Flowable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class FlowableUtils {

  private FlowableUtils() {}

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
                Flowable.interval(intervalMillis, TimeUnit.MILLISECONDS)
                    .map(_ -> scheduledProducer.get())
                    .takeUntil(shared.ignoreElements().toFlowable())));
  }
}
