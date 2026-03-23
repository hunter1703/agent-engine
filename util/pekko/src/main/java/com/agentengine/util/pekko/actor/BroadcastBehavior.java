package com.agentengine.util.pekko.actor;

import com.agentengine.util.pekko.PekkoSerializable;
import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Terminated; // 1. Import Terminated
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

import java.util.LinkedHashSet;
import java.util.Set;

public final class BroadcastBehavior extends AbstractBehavior<BroadcastBehavior.Command> {
  private final Set<ActorRef<Object>> subscribers = new LinkedHashSet<>();

  private BroadcastBehavior(final ActorContext<Command> context) {
    super(context);
  }

  public static Behavior<Command> create() {
    return Behaviors.setup(BroadcastBehavior::new);
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
            .onMessage(Command.Publish.class, this::onPublish)
            .onMessage(Command.Subscribe.class, this::onSubscribe)
            .onMessage(Command.Unsubscribe.class, this::onUnsubscribe)
            .onMessage(Command.Stop.class, msg -> Behaviors.stopped())
            // 2. Handle the signal when a watched actor dies
            .onSignal(Terminated.class, this::onTerminated)
            .build();
  }

  private Behavior<Command> onPublish(final Command.Publish msg) {
    subscribers.forEach(subscriber -> subscriber.tell(msg.event()));
    return this;
  }

  private Behavior<Command> onSubscribe(final Command.Subscribe msg) {
    ActorRef<Object> sub = msg.subscriber();
    if (!subscribers.contains(sub)) {
      subscribers.add(sub);
      // 3. Start watching the subscriber's lifecycle
      getContext().watch(sub);
    }
    if (msg.replyTo() != null) {
      msg.replyTo().tell(Done.done());
    }
    return this;
  }

  private Behavior<Command> onUnsubscribe(final Command.Unsubscribe msg) {
    // 4. Stop watching when they explicitly leave
    getContext().unwatch(msg.subscriber());
    subscribers.remove(msg.subscriber());
    return this;
  }

  // 5. Logic to remove the dead subscriber from the set
  private Behavior<Command> onTerminated(final Terminated terminated) {
    ActorRef<Void> deadActor = terminated.getRef();
    subscribers.remove(deadActor);
    return this;
  }

  public sealed interface Command extends PekkoSerializable {
    record Publish(Object event) implements Command {}
    record Subscribe(ActorRef<Object> subscriber, ActorRef<Done> replyTo) implements Command {}
    record Unsubscribe(ActorRef<Object> subscriber) implements Command {}
    record Stop() implements Command {}
  }
}
