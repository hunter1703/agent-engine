package com.agentengine.scheduler.core.actor;

import com.agentengine.scheduler.api.store.TriggerDefinitionRepository;
import com.agentengine.util.context.Context;
import com.agentengine.util.context.Contextual;
import com.agentengine.util.context.UserContext;
import com.agentengine.util.pekko.PekkoSerializable;
import com.agentengine.util.pekko.actor.ContextualInterceptor;
import java.time.Duration;
import java.util.UUID;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;

public class TriggerReconcilerActor extends AbstractBehavior<TriggerReconcilerActor.Command> {

  private static final String SWEEP_TIMER_KEY = "sweep";
  private static final Duration SWEEP_INTERVAL = Duration.ofMinutes(1);

  private final TriggerDefinitionRepository triggerDefinitionRepository;
  private final long heartbeatTimeoutMs;

  public TriggerReconcilerActor(
      final ActorContext<Command> context,
      final TimerScheduler<Command> timers,
      final TriggerDefinitionRepository triggerDefinitionRepository,
      final long heartbeatTimeoutMs) {
    super(context);
    this.triggerDefinitionRepository = triggerDefinitionRepository;
    this.heartbeatTimeoutMs = heartbeatTimeoutMs;

    timers.startTimerWithFixedDelay(SWEEP_TIMER_KEY, new Command.Sweep(), SWEEP_INTERVAL);
  }

  public static Behavior<Command> create(
      final TriggerDefinitionRepository triggerDefinitionRepository,
      final long heartbeatTimeoutMs) {
    return Behaviors.intercept(
        () ->
            new ContextualInterceptor<>(
                Command.class, new Context(UUID.randomUUID().toString(), UserContext.SYSTEM)),
        Behaviors.setup(
            context ->
                Behaviors.withTimers(
                    timers ->
                        new TriggerReconcilerActor(
                            context, timers, triggerDefinitionRepository, heartbeatTimeoutMs))));
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder().onMessage(Command.Sweep.class, _ -> onSweep()).build();
  }

  private Behavior<Command> onSweep() {
    long reclaimed = triggerDefinitionRepository.recoverTriggers(heartbeatTimeoutMs);
    if (reclaimed > 0) {
      getContext().getLog().info("Reclaimed {} dead triggers back to WAITING status", reclaimed);
    }
    return Behaviors.same();
  }

  public interface Command extends Contextual, PekkoSerializable {
    record Sweep() implements Command {}
  }
}
