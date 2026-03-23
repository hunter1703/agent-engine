package com.agentengine.util.pekko.events;

import com.typesafe.config.ConfigFactory;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SingleChannelTest {

  private static final ActorTestKit kit = ActorTestKit.create(ConfigFactory.parseString("""
      pekko {
        actor.provider = cluster
        remote.artery.canonical.hostname = "127.0.0.1"
        remote.artery.canonical.port = 0
        cluster.seed-nodes = []
      }
      """));
  private static final Materializer mat = Materializer.createMaterializer(kit.system());

  @AfterAll
  static void teardown() {
    kit.shutdownTestKit();
  }

  @Test
  void shouldDeliverPublishedEventsToSubscribers() throws Exception {
    final var channel = new PekkoEventChannel.SingleChannel<String>(kit.system());
    final List<String> received = new ArrayList<>();

    final Publisher<String> stream = channel.events();
    Source.fromPublisher(stream).take(2).runForeach(received::add, mat);

    channel.publish("first");
    channel.publish("second");

    // Give async stream time to process
    Thread.sleep(200);
    assertThat(received).containsExactly("first", "second");
  }

  @Test
  void shouldDeliverOnlyEventsPublishedAfterSubscriberAttaches() {
    final var channel = new PekkoEventChannel.SingleChannel<String>(kit.system());
    channel.publish("early");

    final var received = Source.fromPublisher(channel.events()).take(1).runWith(Sink.seq(), mat).toCompletableFuture();
    channel.publish("late");

    assertThat(received.orTimeout(2, TimeUnit.SECONDS).join()).containsExactly("late");
  }
}
