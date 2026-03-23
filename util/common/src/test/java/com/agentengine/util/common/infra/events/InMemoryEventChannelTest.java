package com.agentengine.util.common.infra.events;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryEventChannelTest {

  @Test
  void shouldDeliverOnlyEventsPublishedAfterSubscriberAttaches() {
    final InMemoryEventChannel<String> channel = new InMemoryEventChannel<>();
    channel.publish("early");

    final TestSubscriber<String> subscriber = Flowable.fromPublisher(channel.events()).test();
    channel.publish("late");

    subscriber.awaitCount(1);
    subscriber.assertValues("late");
  }

  @Test
  void shouldFanOutLiveEventsToConcurrentSubscribers() {
    final InMemoryEventChannel<String> channel = new InMemoryEventChannel<>();
    final TestSubscriber<String> first = Flowable.fromPublisher(channel.events()).test();
    final TestSubscriber<String> second = Flowable.fromPublisher(channel.events()).test();

    channel.publish("shared");

    first.awaitCount(1).assertValues("shared");
    second.awaitCount(1).assertValues("shared");
    assertThat(first.values()).containsExactly("shared");
    assertThat(second.values()).containsExactly("shared");
  }
}
