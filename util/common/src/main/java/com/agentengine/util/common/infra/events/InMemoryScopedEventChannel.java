package com.agentengine.util.common.infra.events;

import org.reactivestreams.Publisher;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

public class InMemoryScopedEventChannel<K, E> implements ScopedEventChannel<K, E> {
  private final ConcurrentMap<K, InMemoryEventChannel<E>> scopes = new ConcurrentHashMap<>();

  @Override
  public Publisher<E> events(final K scope) {
    return channel(scope).events();
  }

  @Override
  public CompletionStage<E> waitFor(final K scope, final Predicate<E> predicate, final Duration timeout) {
    return channel(scope).waitFor(predicate, timeout);
  }

  @Override
  public void publish(final K scope, final E event) {
    channel(scope).publish(event);
  }

  @Override
  public void complete(final K scope) {
    final InMemoryEventChannel<E> channel = scopes.remove(scope);
    if (channel != null) {
      channel.complete();
    }
  }

  @Override
  public void fail(final K scope, final Throwable throwable) {
    final InMemoryEventChannel<E> channel = scopes.remove(scope);
    if (channel != null) {
      channel.fail(throwable);
    }
  }

  private InMemoryEventChannel<E> channel(final K scope) {
    return scopes.computeIfAbsent(scope, ignored -> new InMemoryEventChannel<>());
  }
}
