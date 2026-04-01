package com.agentengine.util.common.events;

import io.reactivex.rxjava3.core.BackpressureOverflowStrategy;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import org.reactivestreams.Publisher;

/**
 * Single-process implementation of {@link EventChannel}.
 *
 * <p>Per-scope linearized subscribe/publish/cancel with per-scope monotonic sequences.
 */
public class InMemoryEventChannel<Scope, Event> implements EventChannel<Scope, Event> {
    private static final int SUBSCRIBER_BUFFER_SIZE = 256;

    private final ConcurrentMap<Scope, ScopeState<Event>> scopes = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<EventSubscription<SequencedEvent<Event>>> subscribe(final Scope scope) {
        return channel(scope).subscribe();
    }

    @Override
    public CompletionStage<Long> publish(final Scope scope, final Event event) {
        return channel(scope).publish(event);
    }

    private ScopeState<Event> channel(final Scope scope) {
        return scopes.computeIfAbsent(scope, ignored -> new ScopeState<>());
    }

    private static final class ScopeState<E> {
        private long nextSequence = 1L;
        private final Map<String, SubscriptionState<E>> subscriptions = new LinkedHashMap<>();
        private final FlowableProcessor<Runnable> commands =
                PublishProcessor.<Runnable>create().toSerialized();

        private CompletionStage<EventSubscription<SequencedEvent<E>>> subscribe() {
            return submit(this::createSubscription);
        }

        private CompletionStage<Long> publish(final E event) {
            return submit(() -> doPublish(event));
        }

        private CompletionStage<Void> cancel(final String subscriptionId) {
            return submit(() -> {
                doCancel(subscriptionId);
                return null;
            });
        }

        private EventSubscription<SequencedEvent<E>> createSubscription() {
            final String subscriptionId = UUID.randomUUID().toString();
            final FlowableProcessor<SequencedEvent<E>> processor =
                    PublishProcessor.<SequencedEvent<E>>create().toSerialized();
            final Publisher<SequencedEvent<E>> publisher = processor
                    .onBackpressureBuffer(SUBSCRIBER_BUFFER_SIZE, () -> {}, BackpressureOverflowStrategy.ERROR)
                    .doFinally(() -> cancel(subscriptionId));

            subscriptions.put(subscriptionId, new SubscriptionState<>(processor));
            return new EventSubscription<>(subscriptionId, publisher, () -> cancel(subscriptionId));
        }

        private long doPublish(final E event) {
            final long sequence = nextSequence++;
            final SequencedEvent<E> sequencedEvent = new SequencedEvent<>(sequence, event);
            final Iterator<Map.Entry<String, SubscriptionState<E>>> iterator =
                    subscriptions.entrySet().iterator();
            while (iterator.hasNext()) {
                final Map.Entry<String, SubscriptionState<E>> entry = iterator.next();
                final SubscriptionState<E> subscriber = entry.getValue();
                if (!subscriber.active()) {
                    iterator.remove();
                    continue;
                }
                try {
                    subscriber.emit(sequencedEvent);
                } catch (final Throwable throwable) {
                    iterator.remove();
                    subscriber.fail(throwable);
                }
            }
            return sequence;
        }

        private void doCancel(final String subscriptionId) {
            final SubscriptionState<E> removed = subscriptions.remove(subscriptionId);
            if (removed != null) {
                removed.complete();
            }
        }

        private <T> CompletionStage<T> submit(final Supplier<T> action) {
            final CompletableFuture<T> result = new CompletableFuture<>();
            commands.onNext(() -> {
                try {
                    result.complete(action.get());
                } catch (final Throwable throwable) {
                    result.completeExceptionally(throwable);
                }
            });
            return result;
        }
    }

    private record SubscriptionState<E>(FlowableProcessor<SequencedEvent<E>> processor) {

        private boolean active() {
            return !processor.hasComplete() && !processor.hasThrowable();
        }

        private void emit(final SequencedEvent<E> event) {
            processor.onNext(event);
        }

        private void complete() {
            if (!processor.hasComplete() && !processor.hasThrowable()) {
                processor.onComplete();
            }
        }

        private void fail(final Throwable throwable) {
            if (!processor.hasComplete() && !processor.hasThrowable()) {
                processor.onError(throwable);
            }
        }
    }
}
