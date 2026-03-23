package com.agentengine.util.common.infra.events;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.Test;

class InMemoryScopedEventChannelTest {

  @Test
  void shouldDeliverOnlyEventsPublishedAfterSubscriberAttachesForScope() {
    final InMemoryScopedEventChannel<String, String> channel = new InMemoryScopedEventChannel<>();
    channel.publish("scope-1", "early");

    final TestSubscriber<String> subscriber = Flowable.fromPublisher(channel.events("scope-1")).test();
    channel.publish("scope-1", "late");

    subscriber.awaitCount(1);
    subscriber.assertValues("late");
  }

  @Test
  void shouldIsolateScopesForLiveSubscribers() {
    final InMemoryScopedEventChannel<String, String> channel = new InMemoryScopedEventChannel<>();
    final TestSubscriber<String> first = Flowable.fromPublisher(channel.events("scope-1")).test();
    final TestSubscriber<String> second = Flowable.fromPublisher(channel.events("scope-2")).test();

    channel.publish("scope-1", "one");
    channel.publish("scope-2", "two");

    first.awaitCount(1).assertValues("one");
    second.awaitCount(1).assertValues("two");
  }
}
