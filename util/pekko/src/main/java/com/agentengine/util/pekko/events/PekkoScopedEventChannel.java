package com.agentengine.util.pekko.events;

import com.agentengine.util.common.infra.events.ScopedEventChannel;
import org.apache.pekko.actor.typed.ActorSystem;
import org.reactivestreams.Publisher;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

/**
 * {@link ScopedEventChannel} where each scope key maps to its own
 * {@link PekkoEventChannel}. Suitable for use with cluster sharding: pass a
 * {@code shardingEntityId} as the scope key so any node can publish/subscribe
 * to the same scope.
 */
public final class PekkoScopedEventChannel<K, E> implements ScopedEventChannel<K, E> {

  private final ActorSystem<?> system;
  private final String channelName;
  private final ConcurrentMap<K, PekkoEventChannel<E>> channels = new ConcurrentHashMap<>();

  public PekkoScopedEventChannel(final ActorSystem<?> system) {
    this(system, "scoped-event-channel-" + java.util.UUID.randomUUID());
  }

  public PekkoScopedEventChannel(final ActorSystem<?> system, final String channelName) {
    this.system = system;
    this.channelName = channelName;
  }

  @Override
  public void publish(final K scope, final E event) {
    channel(scope).publish(event);
  }

  @Override
  public Publisher<E> events(final K scope) {
    return channel(scope).events();
  }

  @Override
  public CompletionStage<E> waitFor(final K scope, final Predicate<E> predicate, final Duration timeout) {
    return channel(scope).waitFor(predicate, timeout);
  }

  @Override
  public void complete(final K scope) {
    final var channel = channels.remove(scope);
    if (channel != null) {
      channel.stop();
    }
  }

  @Override
  public void fail(final K scope, final Throwable throwable) {
    final var channel = channels.remove(scope);
    if (channel != null) {
      channel.stop();
    }
  }

  private PekkoEventChannel<E> channel(final K scope) {
    return channels.computeIfAbsent(scope, k -> new PekkoEventChannel<>(system, channelName, String.valueOf(scope)));
  }
}
