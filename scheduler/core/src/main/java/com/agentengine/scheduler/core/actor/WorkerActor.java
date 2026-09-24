package com.agentengine.scheduler.core.actor;

import com.agentengine.scheduler.api.models.TriggerDefinition;
import com.agentengine.scheduler.api.store.TriggerDefinitionRepository;
import com.agentengine.scheduler.core.SchedulerConfigs;
import com.agentengine.util.common.EnvUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.context.Context;
import com.agentengine.util.context.Contextual;
import com.agentengine.util.context.UserContext;
import com.agentengine.util.pekko.PekkoSerializable;
import com.agentengine.util.pekko.actor.ContextualInterceptor;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;

/**
 * One per pod. Asks the scheduler for as many triggers as the pod has room for and starts a {@link
 * JobRunnerActor} for each. A pod that is full or slow simply asks for less.
 */
public final class WorkerActor extends AbstractBehavior<WorkerActor.Command> {

  private static final String POLL_TIMER_KEY = "poll";

  private final ActorRef<SchedulerActor.Command> scheduler;
  private final TriggerDefinitionRepository triggerDefinitionRepository;
  private final Executor jobExecutor;
  private final SchedulerConfigs schedulerConfigs;
  private final String workerId = newWorkerId();
  private int running;
  private boolean requesting;

  private WorkerActor(
      final ActorContext<Command> context,
      final TimerScheduler<Command> timers,
      final ActorRef<SchedulerActor.Command> scheduler,
      final TriggerDefinitionRepository triggerDefinitionRepository,
      final Executor jobExecutor,
      final SchedulerConfigs schedulerConfigs) {
    super(context);
    this.scheduler = scheduler;
    this.triggerDefinitionRepository = triggerDefinitionRepository;
    this.jobExecutor = jobExecutor;
    this.schedulerConfigs = schedulerConfigs;
    context.getSelf().tell(new Command.Poll());
    timers.startTimerWithFixedDelay(
        POLL_TIMER_KEY, new Command.Poll(), schedulerConfigs.pollInterval());
  }

  public static Behavior<Command> create(
      final ActorRef<SchedulerActor.Command> scheduler,
      final TriggerDefinitionRepository triggerDefinitionRepository,
      final Executor jobExecutor,
      final SchedulerConfigs schedulerConfigs) {
    return Behaviors.intercept(
        () ->
            new ContextualInterceptor<>(
                Command.class, new Context(UUID.randomUUID().toString(), UserContext.SYSTEM)),
        Behaviors.setup(
            context ->
                Behaviors.withTimers(
                    timers ->
                        new WorkerActor(
                            context,
                            timers,
                            scheduler,
                            triggerDefinitionRepository,
                            jobExecutor,
                            schedulerConfigs))));
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
        .onMessage(Command.Poll.class, _ -> onPoll())
        .onMessage(Command.WorkReceived.class, this::onWorkReceived)
        .onMessage(Command.RunCompleted.class, this::onRunCompleted)
        .onMessage(Command.TriggerRescheduled.class, this::onTriggerRescheduled)
        .onMessage(Command.RunnerStopped.class, _ -> onRunnerStopped())
        .build();
  }

  private Behavior<Command> onPoll() {
    requestWork();
    return this;
  }

  private Behavior<Command> onWorkReceived(final Command.WorkReceived command) {
    requesting = false;
    if (command.failure() != null) {
      getContext()
          .getLog()
          .warn("Scheduler did not answer the request for work", command.failure());
      return this;
    }
    command.triggers().forEach(this::run);
    if (!command.triggers().isEmpty()) {
      requestWork();
    }
    return this;
  }

  private Behavior<Command> onRunCompleted(final Command.RunCompleted command) {
    scheduler.tell(new SchedulerActor.Command.RunFinished(command.triggerId()));
    return this;
  }

  private Behavior<Command> onTriggerRescheduled(final Command.TriggerRescheduled command) {
    scheduler.tell(new SchedulerActor.Command.JobScheduled(command.trigger()));
    return this;
  }

  private Behavior<Command> onRunnerStopped() {
    running--;
    requestWork();
    return this;
  }

  private void requestWork() {
    final int freeSlots = schedulerConfigs.maxConcurrentJobs() - running;
    if (requesting || freeSlots <= 0) {
      return;
    }
    requesting = true;
    getContext()
        .ask(
            SchedulerActor.Response.WorkGranted.class,
            scheduler,
            schedulerConfigs.workRequestTimeout(),
            replyTo -> new SchedulerActor.Command.RequestWork(workerId, freeSlots, replyTo),
            (granted, failure) ->
                new Command.WorkReceived(
                    granted == null ? List.of() : granted.triggers(), failure));
  }

  private void run(final TriggerDefinition trigger) {
    final ActorRef<JobRunnerActor.Command> runner =
        getContext()
            .spawnAnonymous(
                JobRunnerActor.create(
                    trigger,
                    workerId,
                    getContext().getSelf(),
                    triggerDefinitionRepository,
                    jobExecutor,
                    schedulerConfigs.heartbeatInterval()));
    getContext().watchWith(runner, new Command.RunnerStopped());
    running++;
  }

  private static String newWorkerId() {
    final String host = EnvUtils.getHostname();
    return (StringUtils.isBlank(host) ? "worker" : host) + ":" + UUID.randomUUID();
  }

  public interface Command extends Contextual, PekkoSerializable {

    record Poll() implements Command {}

    record WorkReceived(List<TriggerDefinition> triggers, Throwable failure) implements Command {}

    record RunCompleted(String triggerId) implements Command {}

    record TriggerRescheduled(TriggerDefinition trigger) implements Command {}

    record RunnerStopped() implements Command {}
  }
}
