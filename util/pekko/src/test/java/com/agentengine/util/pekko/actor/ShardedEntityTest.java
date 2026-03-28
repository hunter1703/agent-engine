package com.agentengine.util.pekko.actor;

import com.agentengine.util.pekko.PekkoSerializable;
import com.typesafe.config.ConfigFactory;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShardedEntityTest {

  private static final ActorTestKit kit = ActorTestKit.create(ConfigFactory.parseString("""
      pekko {
        loglevel = WARNING
        actor.provider = local
        persistence {
          journal.plugin = "pekko.persistence.journal.inmem"
          snapshot-store.plugin = "pekko.persistence.no-snapshot-store"
        }
      }
      """));

  @AfterAll
  static void teardown() {
    kit.shutdownTestKit();
  }

  // Minimal counter entity for testing
  interface CounterCmd extends PekkoSerializable {
    record Increment(ActorRef<Integer> replyTo) implements CounterCmd {
    }

    record GetCount(ActorRef<Integer> replyTo) implements CounterCmd {
    }
  }

  record Incremented() implements PekkoSerializable {
  }

  record CounterState(int count) {
    static CounterState empty() {
      return new CounterState(0);
    }
  }

  static class CounterEntity extends ShardedEntity<CounterCmd, Incremented, CounterState> {

    static final EntityTypeKey<CounterCmd> TYPE_KEY = EntityTypeKey.create(CounterCmd.class, "Counter");

    CounterEntity(final String entityId) {
      super(TYPE_KEY.name(), entityId);
    }

    @Override
    public CounterState emptyState() {
      return CounterState.empty();
    }

    @Override
    public CommandHandler<CounterCmd, Incremented, CounterState> commandHandler() {
      return newCommandHandlerBuilder().forAnyState()
          .onCommand(CounterCmd.Increment.class,
              (state, incrementCommand) -> Effect().persist(new Incremented())
                  .thenRun(updatedState -> incrementCommand.replyTo().tell(updatedState.count())))
          .onCommand(CounterCmd.GetCount.class, (state, getCountCommand) -> {
            getCountCommand.replyTo().tell(state.count());
            return Effect().none();
          }).build();
    }

    @Override
    public EventHandler<CounterState, Incremented> eventHandler() {
      return newEventHandlerBuilder().forAnyState()
          .onEvent(Incremented.class, (state, incrementedEvent) -> new CounterState(state.count() + 1)).build();
    }
  }

  @Test
  void shouldRespondToInitialState() {
    final ActorRef<CounterCmd> entity = kit.spawn(new CounterEntity("counter-initial"));
    final TestProbe<Integer> probe = kit.createTestProbe();

    entity.tell(new CounterCmd.GetCount(probe.ref()));
    assertThat(probe.receiveMessage()).isEqualTo(0);
  }

  @Test
  void shouldPersistAndRecoverState() {
    final ActorRef<CounterCmd> entity = kit.spawn(new CounterEntity("counter-1"));
    final TestProbe<Integer> probe = kit.createTestProbe();

    entity.tell(new CounterCmd.Increment(probe.ref()));
    probe.expectMessage(1);

    entity.tell(new CounterCmd.Increment(probe.ref()));
    probe.expectMessage(2);

    entity.tell(new CounterCmd.GetCount(probe.ref()));
    assertThat(probe.receiveMessage()).isEqualTo(2);
  }
}
