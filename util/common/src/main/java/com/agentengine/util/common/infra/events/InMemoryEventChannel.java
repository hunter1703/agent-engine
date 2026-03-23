package com.agentengine.util.common.infra.events;

import com.agentengine.util.common.CompletionUtils;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.reactivestreams.Publisher;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class InMemoryEventChannel<Scope, Event> implements EventChannel<Scope, Event> {
  private final ConcurrentMap<Scope, SingleChannel<Event>> scopes = new ConcurrentHashMap<>();

  @Override
  public Publisher<Event> events(final Scope scope) {
    return channel(scope).events();
  }

  @Override
  public CompletionStage<Event> waitFor(final Scope scope, final Predicate<Event> predicate, final Duration timeout) {
    return channel(scope).waitFor(predicate, timeout);
  }

  @Override
  public void publish(final Scope scope, final Event event) {
    channel(scope).publish(event);
  }

  private SingleChannel<Event> channel(final Scope scope) {
    return scopes.computeIfAbsent(scope, ignored -> new SingleChannel<>());
  }

  public static class SingleChannel<E> {

    private final FlowableProcessor<E> processor = PublishProcessor.<E>create().toSerialized();

    public void publish(final E event) {
      processor.onNext(event);
    }

    public Publisher<E> events() {
      return processor.onBackpressureBuffer();
    }

    public CompletionStage<E> waitFor(final Predicate<E> predicate, final Duration timeout) {
      return CompletionUtils.completeWithRootCause(Flowable.fromPublisher(events()).filter(predicate::test).firstOrError().timeout(timeout.toMillis(), TimeUnit.MILLISECONDS).toCompletionStage());
    }
  }
}
