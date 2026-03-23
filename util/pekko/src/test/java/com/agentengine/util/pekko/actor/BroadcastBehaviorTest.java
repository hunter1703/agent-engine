package com.agentengine.util.pekko.actor;

import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class BroadcastBehaviorTest {

  private static final ActorTestKit kit = ActorTestKit.create();

  @AfterAll
  static void teardown() {
    kit.shutdownTestKit();
  }

  @Test
  void shouldBroadcastToAllSubscribers() {
    final var actor = kit.spawn(BroadcastBehavior.create(), "broadcast");
    final TestProbe<Object> sub1 = kit.createTestProbe();
    final TestProbe<Object> sub2 = kit.createTestProbe();

    actor.tell(new BroadcastBehavior.Command.Subscribe(asObjectRef(sub1.ref()), null));
    actor.tell(new BroadcastBehavior.Command.Subscribe(asObjectRef(sub2.ref()), null));
    actor.tell(new BroadcastBehavior.Command.Publish("hello"));

    sub1.expectMessage("hello");
    sub2.expectMessage("hello");
  }

  @Test
  void shouldNotReceiveAfterUnsubscribe() {
    final var actor = kit.spawn(BroadcastBehavior.create(), "unsub-test");
    final TestProbe<Object> sub = kit.createTestProbe();

    actor.tell(new BroadcastBehavior.Command.Subscribe(asObjectRef(sub.ref()), null));
    actor.tell(new BroadcastBehavior.Command.Unsubscribe(asObjectRef(sub.ref())));
    actor.tell(new BroadcastBehavior.Command.Publish("ignored"));

    sub.expectNoMessage();
  }

  @SuppressWarnings("unchecked")
  private static ActorRef<Object> asObjectRef(final ActorRef<?> ref) {
    return (ActorRef<Object>) ref;
  }
}
