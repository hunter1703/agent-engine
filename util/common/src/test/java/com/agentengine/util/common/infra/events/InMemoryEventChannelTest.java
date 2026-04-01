package com.agentengine.util.common.infra.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.util.common.events.EventSubscription;
import com.agentengine.util.common.events.InMemoryEventChannel;
import com.agentengine.util.common.events.SequencedEvent;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

public class InMemoryEventChannelTest {

    @Test
    public void shouldDeliverOnlyEventsPublishedAfterSubscribeConfirmation() {
        final InMemoryEventChannel<String, String> channel = new InMemoryEventChannel<>();
        join(channel.publish("scope-1", "early"));

        final EventSubscription<SequencedEvent<String>> subscription = join(channel.subscribe("scope-1"));
        final TestSubscriber<SequencedEvent<String>> subscriber =
                Flowable.fromPublisher(subscription.publisher()).test();

        join(channel.publish("scope-1", "late"));

        subscriber.awaitCount(1);
        subscriber.assertValueCount(1);
        assertThat(subscriber.values().getFirst().payload()).isEqualTo("late");
        assertThat(subscriber.values().getFirst().sequence()).isEqualTo(2L);
    }

    @Test
    public void shouldLinearizeCancelAndStopFurtherDeliveryAfterConfirmation() {
        final InMemoryEventChannel<String, String> channel = new InMemoryEventChannel<>();
        final EventSubscription<SequencedEvent<String>> subscription = join(channel.subscribe("scope-cancel"));
        final TestSubscriber<SequencedEvent<String>> subscriber =
                Flowable.fromPublisher(subscription.publisher()).test();

        join(channel.publish("scope-cancel", "before-cancel"));
        subscriber.awaitCount(1);

        join(subscription.cancel());
        join(channel.publish("scope-cancel", "after-cancel"));

        subscriber.awaitDone(2, TimeUnit.SECONDS);
        assertThat(subscriber.values()).extracting(SequencedEvent::payload).containsExactly("before-cancel");
    }

    @Test
    public void shouldUseMonotonicSequencePerScope() {
        final InMemoryEventChannel<String, String> channel = new InMemoryEventChannel<>();
        final long first = join(channel.publish("scope-seq", "one"));
        final long second = join(channel.publish("scope-seq", "two"));
        final long third = join(channel.publish("scope-seq", "three"));

        assertThat(first).isEqualTo(1L);
        assertThat(second).isEqualTo(2L);
        assertThat(third).isEqualTo(3L);
    }

    @Test
    public void shouldIsolateScopes() {
        final InMemoryEventChannel<String, String> channel = new InMemoryEventChannel<>();
        final EventSubscription<SequencedEvent<String>> scopeOne = join(channel.subscribe("scope-one"));
        final EventSubscription<SequencedEvent<String>> scopeTwo = join(channel.subscribe("scope-two"));
        final TestSubscriber<SequencedEvent<String>> first =
                Flowable.fromPublisher(scopeOne.publisher()).test();
        final TestSubscriber<SequencedEvent<String>> second =
                Flowable.fromPublisher(scopeTwo.publisher()).test();

        join(channel.publish("scope-one", "one"));
        join(channel.publish("scope-two", "two"));

        first.awaitCount(1);
        second.awaitCount(1);
        assertThat(first.values()).extracting(SequencedEvent::payload).containsExactly("one");
        assertThat(second.values()).extracting(SequencedEvent::payload).containsExactly("two");
    }

    @Test
    public void shouldFailSlowSubscriberOnOverflow() {
        final InMemoryEventChannel<String, String> channel = new InMemoryEventChannel<>();
        final EventSubscription<SequencedEvent<String>> subscription = join(channel.subscribe("scope-overflow"));
        final TestSubscriber<SequencedEvent<String>> subscriber = new TestSubscriber<>(0L);
        Flowable.fromPublisher(subscription.publisher()).subscribe(subscriber);

        for (int i = 0; i < 400; i++) {
            join(channel.publish("scope-overflow", "event-" + i));
        }

        subscriber.awaitDone(2, TimeUnit.SECONDS);
        subscriber.assertError(Throwable.class);
    }

    @Test
    public void shouldDeliverParallelPublishesInStrictSequenceOrder() throws InterruptedException {
        final InMemoryEventChannel<String, String> channel = new InMemoryEventChannel<>();
        final EventSubscription<SequencedEvent<String>> subscription = join(channel.subscribe("scope-parallel-order"));
        final TestSubscriber<SequencedEvent<String>> subscriber =
                Flowable.fromPublisher(subscription.publisher()).test();

        final int publishCount = 200;
        final ExecutorService executor = Executors.newFixedThreadPool(8);
        final CountDownLatch start = new CountDownLatch(1);
        try {
            final List<CompletableFuture<Long>> publishes = IntStream.range(0, publishCount)
                    .mapToObj(index -> CompletableFuture.supplyAsync(
                            () -> {
                                try {
                                    start.await(5, TimeUnit.SECONDS);
                                } catch (final InterruptedException exception) {
                                    Thread.currentThread().interrupt();
                                    throw new RuntimeException(exception);
                                }
                                return join(channel.publish("scope-parallel-order", "event-" + index));
                            },
                            executor))
                    .toList();

            start.countDown();
            publishes.forEach(stage -> join(stage));

            subscriber.awaitCount(publishCount);
            subscriber.assertValueCount(publishCount);

            final List<Long> deliveredSequences =
                    subscriber.values().stream().map(SequencedEvent::sequence).toList();
            assertThat(deliveredSequences)
                    .containsExactlyElementsOf(
                            LongStream.rangeClosed(1, publishCount).boxed().toList());
        } finally {
            executor.shutdownNow();
        }
    }

    private static <T> T join(final CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }
}
