package com.agentengine.util.pekko.events;

import com.agentengine.util.common.events.EventSubscription;
import com.agentengine.util.common.events.SequencedEvent;
import com.typesafe.config.ConfigFactory;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.SpawnProtocol;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.cluster.typed.Join;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.SinkQueueWithCancel;
import org.apache.pekko.stream.javadsl.Source;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SingleChannelTest {

  private static final ActorSystem<SpawnProtocol.Command> system = ActorSystem.create(SpawnProtocol.create(), "single-channel-test-system",
      ConfigFactory.parseString("""
          pekko {
            loglevel = "WARNING"
            actor.provider = cluster
            remote.artery.canonical.hostname = "127.0.0.1"
            remote.artery.canonical.port = 0
            cluster.seed-nodes = []
            persistence {
              journal.plugin = "pekko.persistence.journal.inmem"
              snapshot-store.plugin = "pekko.persistence.no-snapshot-store"
            }
            actor.serialization-bindings {
              "com.agentengine.util.pekko.PekkoSerializable" = jackson-json
            }
          }
          """));
  private static final Materializer mat = Materializer.createMaterializer(system);

  static {
    final Cluster cluster = Cluster.get(system);
    cluster.manager().tell(Join.create(cluster.selfMember().address()));
  }

  @AfterAll
  static void teardown() {
    system.terminate();
  }

  @Test
  void shouldDeliverOnlyEventsPublishedAfterSubscribeConfirmation() {
    final PekkoEventChannel<String, String> channel = new PekkoEventChannel<>(system, "pekko-channel-ordering");
    ClusterSharding.get(system).init(channel.entity());
    final String scope = "scope-ordering";

    join(channel.publish(scope, "early"));
    final EventSubscription<SequencedEvent<String>> subscription = join(channel.subscribe(scope));
    final SinkQueueWithCancel<SequencedEvent<String>> queue = Source.fromPublisher(subscription.publisher()).runWith(Sink.queue(), mat);

    join(channel.publish(scope, "late"));
    final SequencedEvent<String> event = queue.pull().toCompletableFuture().orTimeout(3, TimeUnit.SECONDS).join().orElseThrow();

    assertThat(event.payload()).isEqualTo("late");
    assertThat(event.sequence()).isEqualTo(2L);
    queue.cancel();
  }

  @Test
  void shouldStopDeliveringAfterCancelConfirmation() {
    final PekkoEventChannel<String, String> channel = new PekkoEventChannel<>(system, "pekko-channel-cancel");
    ClusterSharding.get(system).init(channel.entity());
    final String scope = "scope-cancel";
    final EventSubscription<SequencedEvent<String>> subscription = join(channel.subscribe(scope));
    final SinkQueueWithCancel<SequencedEvent<String>> queue = Source.fromPublisher(subscription.publisher()).runWith(Sink.queue(), mat);

    join(channel.publish(scope, "before-cancel"));
    final SequencedEvent<String> first = queue.pull().toCompletableFuture().orTimeout(3, TimeUnit.SECONDS).join().orElseThrow();
    assertThat(first.payload()).isEqualTo("before-cancel");

    join(subscription.cancel());
    join(channel.publish(scope, "after-cancel"));

    final Optional<SequencedEvent<String>> next = queue.pull().toCompletableFuture().orTimeout(3, TimeUnit.SECONDS).join();
    assertThat(next).isEmpty();
    queue.cancel();
  }

  @Test
  void shouldFailSlowSubscriberOnOverflow() {
    final PekkoEventChannel<String, String> channel = new PekkoEventChannel<>(system, "pekko-channel-overflow");
    ClusterSharding.get(system).init(channel.entity());
    final String scope = "scope-overflow";
    final EventSubscription<SequencedEvent<String>> subscription = join(channel.subscribe(scope));

    for (int i = 0; i < 400; i++) {
      join(channel.publish(scope, "event-" + i));
    }

    final CompletableFuture<List<SequencedEvent<String>>> stream = Source.fromPublisher(subscription.publisher()).runWith(Sink.seq(), mat)
        .toCompletableFuture();
    assertThatThrownBy(() -> stream.orTimeout(5, TimeUnit.SECONDS).join()).isInstanceOf(RuntimeException.class);
  }

  @Test
  void shouldDeliverParallelPublishesInStrictSequenceOrder() {
    final PekkoEventChannel<String, String> channel = new PekkoEventChannel<>(system, "pekko-channel-parallel-order");
    ClusterSharding.get(system).init(channel.entity());
    final String scope = "scope-parallel-order";
    final EventSubscription<SequencedEvent<String>> subscription = join(channel.subscribe(scope));
    final SinkQueueWithCancel<SequencedEvent<String>> queue = Source.fromPublisher(subscription.publisher()).runWith(Sink.queue(), mat);

    final int publishCount = 200;
    final List<CompletableFuture<Long>> publishStages = IntStream.range(0, publishCount)
        .mapToObj(index -> channel.publish(scope, "event-" + index).toCompletableFuture()).toList();
    CompletableFuture.allOf(publishStages.toArray(new CompletableFuture[0])).orTimeout(10, TimeUnit.SECONDS).join();

    final List<Long> deliveredSequences = IntStream.range(0, publishCount)
        .mapToObj(ignored -> queue.pull().toCompletableFuture().orTimeout(10, TimeUnit.SECONDS).join().orElseThrow().sequence()).toList();

    assertThat(deliveredSequences).containsExactlyElementsOf(LongStream.rangeClosed(1, publishCount).boxed().toList());
    queue.cancel();
  }

  private static <T> T join(final CompletionStage<T> stage) {
    return stage.toCompletableFuture().join();
  }
}
