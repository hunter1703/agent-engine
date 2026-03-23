package com.agentengine.util.common.infra.events;

import com.agentengine.util.common.CompletionUtils;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.reactivestreams.Publisher;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * In-memory {@link EventChannel} backed by an RxJava3 {@link PublishProcessor}.
 * Suitable for tests and single-node deployments.
 */
public class InMemoryEventChannel<E> implements EventChannel<E> {

  private final FlowableProcessor<E> processor = PublishProcessor.<E>create().toSerialized();

  @Override
  public void publish(final E event) {
    processor.onNext(event);
  }

  @Override
  public Publisher<E> events() {
    return processor.onBackpressureBuffer();
  }

  @Override
  public CompletionStage<E> waitFor(final Predicate<E> predicate, final Duration timeout) {
    return CompletionUtils.completeWithRootCause(Flowable.fromPublisher(events()).filter(predicate::test).firstOrError().timeout(timeout.toMillis(), TimeUnit.MILLISECONDS).toCompletionStage());
  }
}
