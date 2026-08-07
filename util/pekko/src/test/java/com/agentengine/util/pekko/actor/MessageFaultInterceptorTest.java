package com.agentengine.util.pekko.actor;

import java.time.Duration;
import java.util.Optional;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class MessageFaultInterceptorTest {

    private static final ActorTestKit TEST_KIT = ActorTestKit.create();

    interface Ping {}

    record PingMessage(String id) implements Ping {}

    @AfterAll
    static void shutdown() {
        TEST_KIT.shutdownTestKit();
    }

    @Test
    void shouldPassThroughWhenNoConfigRegistered() {
        final ChaosMailboxRegistry registry = new ChaosMailboxRegistry();
        final TestProbe<Ping> probe = TEST_KIT.createTestProbe();
        final ActorRef<Ping> ref = spawnIntercepted(registry, "entity-1", probe);

        ref.tell(new PingMessage("hello"));

        probe.expectMessage(new PingMessage("hello"));
    }

    @Test
    void shouldDropAllMessagesWhenConfigActiveWithFullDropRate() {
        final ChaosMailboxRegistry registry = new ChaosMailboxRegistry();
        registry.register("entity-2", new ChaosMailboxConfig(1.0, Optional.empty(), 0.0, true));
        final TestProbe<Ping> probe = TEST_KIT.createTestProbe();
        final ActorRef<Ping> ref = spawnIntercepted(registry, "entity-2", probe);

        ref.tell(new PingMessage("dropped"));

        probe.expectNoMessage(Duration.ofMillis(300));
    }

    @Test
    void shouldDelayMessagesWhenConfigActiveWithFullDelayRate() {
        final ChaosMailboxRegistry registry = new ChaosMailboxRegistry();
        registry.register("entity-3", new ChaosMailboxConfig(0.0, Optional.of(Duration.ofMillis(300)), 1.0, true));
        final TestProbe<Ping> probe = TEST_KIT.createTestProbe();
        final ActorRef<Ping> ref = spawnIntercepted(registry, "entity-3", probe);

        ref.tell(new PingMessage("delayed"));

        probe.expectNoMessage(Duration.ofMillis(100));
        probe.expectMessage(Duration.ofSeconds(2), new PingMessage("delayed"));
    }

    @Test
    void shouldStopDroppingImmediatelyAfterFaultIsRemoved() {
        final ChaosMailboxRegistry registry = new ChaosMailboxRegistry();
        registry.register("entity-4", new ChaosMailboxConfig(1.0, Optional.empty(), 0.0, true));
        final TestProbe<Ping> probe = TEST_KIT.createTestProbe();
        final ActorRef<Ping> ref = spawnIntercepted(registry, "entity-4", probe);

        ref.tell(new PingMessage("dropped"));
        probe.expectNoMessage(Duration.ofMillis(200));

        registry.remove("entity-4");
        ref.tell(new PingMessage("recovered"));

        probe.expectMessage(new PingMessage("recovered"));
    }

    private static ActorRef<Ping> spawnIntercepted(
            final ChaosMailboxRegistry registry, final String entityId, final TestProbe<Ping> probe) {
        final Behavior<Ping> inner = Behaviors.receiveMessage(msg -> {
            probe.getRef().tell(msg);
            return Behaviors.same();
        });
        return TEST_KIT.spawn(
                Behaviors.intercept(() -> new MessageFaultInterceptor<>(Ping.class, entityId, registry), inner));
    }
}
