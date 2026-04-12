package com.agentengine.util.common.events;

import com.agentengine.util.common.CompletionUtils;
import io.reactivex.rxjava3.core.Flowable;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Scoped, logically-immortal live event channel.
 *
 * <p>Delivery contract:
 *
 * <ul>
 *   <li>{@link #subscribe} linearizes at acknowledgement: all events published after the returned
 *       stage completes are delivered at least once to the subscriber's mailbox.
 *   <li>{@link #publish} linearizes at acknowledgement and returns the assigned monotonic sequence.
 *   <li>Cancellation happens automatically when the stream terminates; broadcaster state is
 *       cleaned up automatically.
 *   <li>If a subscriber dies, it must re-subscribe; the channel cleans up stale state
 *       automatically.
 * </ul>
 */
public interface EventChannel<Scope, Event> {

    CompletionStage<EventSubscription<SequencedEvent<Event>>> subscribe(Scope scope);

    CompletionStage<Long> publish(Scope scope, Event event);

    default CompletionStage<SequencedEvent<Event>> waitFor(
            final Scope scope, final Predicate<SequencedEvent<Event>> predicate, final Duration timeout) {
        return subscribe(scope)
                .thenCompose(subscription ->
                        CompletionUtils.completeWithRootCause(Flowable.fromPublisher(subscription.publisher())
                                .filter(predicate::test)
                                .firstOrError()
                                .timeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                                .toCompletionStage()));
    }
}
