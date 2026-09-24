package com.agentengine.scheduler.core.actor;

import static com.agentengine.scheduler.core.SchedulerUtils.JITTER_FRACTION;

import com.agentengine.scheduler.api.models.JobDefinition;
import com.agentengine.scheduler.api.models.TriggerDefinition;
import com.agentengine.scheduler.api.models.TriggerStatus;
import com.agentengine.scheduler.api.runner.Job;
import com.agentengine.scheduler.api.runner.JobContext;
import com.agentengine.scheduler.api.runner.JobResult;
import com.agentengine.scheduler.api.store.TriggerDefinitionRepository;
import com.agentengine.scheduler.core.CronUtils;
import com.agentengine.scheduler.core.SchedulerUtils;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.context.Context;
import com.agentengine.util.context.Contextual;
import com.agentengine.util.pekko.PekkoSerializable;
import com.agentengine.util.pekko.actor.ContextualInterceptor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;

/**
 * Runs one trigger for one run, then stops. It starts the trigger only if it is still queued for
 * its worker, keeps the trigger's lease alive while the job runs, and records how the run ends.
 */
public final class JobRunnerActor extends AbstractBehavior<JobRunnerActor.Command> {

  private static final String HEARTBEAT_TIMER_KEY = "heartbeat";

  private final TriggerDefinition trigger;
  private final String scheduledBy;
  private final ActorRef<WorkerActor.Command> worker;
  private final TriggerDefinitionRepository triggerDefinitionRepository;
  private final Executor jobExecutor;
  private final TimerScheduler<Command> timers;
  private final Duration heartbeatInterval;

  private JobRunnerActor(
      final ActorContext<Command> context,
      final TimerScheduler<Command> timers,
      final TriggerDefinition trigger,
      final String scheduledBy,
      final ActorRef<WorkerActor.Command> worker,
      final TriggerDefinitionRepository triggerDefinitionRepository,
      final Executor jobExecutor,
      final Duration heartbeatInterval) {
    super(context);
    this.timers = timers;
    this.trigger = trigger;
    this.scheduledBy = scheduledBy;
    this.worker = worker;
    this.triggerDefinitionRepository = triggerDefinitionRepository;
    this.jobExecutor = jobExecutor;
    this.heartbeatInterval = heartbeatInterval;
    context.getSelf().tell(new Command.Start());
  }

  public static Behavior<Command> create(
      final TriggerDefinition trigger,
      final String scheduledBy,
      final ActorRef<WorkerActor.Command> worker,
      final TriggerDefinitionRepository triggerDefinitionRepository,
      final Executor jobExecutor,
      final Duration heartbeatInterval) {
    return Behaviors.intercept(
        () ->
            new ContextualInterceptor<>(
                Command.class,
                new Context(trigger.getId(), trigger.getJobDefinition().getUserContext())),
        Behaviors.setup(
            context ->
                Behaviors.withTimers(
                    timers ->
                        new JobRunnerActor(
                            context,
                            timers,
                            trigger,
                            scheduledBy,
                            worker,
                            triggerDefinitionRepository,
                            jobExecutor,
                            heartbeatInterval))));
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
        .onMessage(Command.Start.class, _ -> onStart())
        .onMessage(Command.RunFinished.class, this::onRunFinished)
        .onMessage(Command.Heartbeat.class, _ -> onHeartbeat())
        .build();
  }

  private Behavior<Command> onStart() {
    if (!markRunning()) {
      return Behaviors.stopped();
    }
    timers.startTimerWithFixedDelay(
        HEARTBEAT_TIMER_KEY, new Command.Heartbeat(), heartbeatInterval);
    final Job job;
    try {
      job = instantiate(trigger);
    } catch (final ReflectiveOperationException | RuntimeException exception) {
      getContext()
          .getLog()
          .error(
              "Could not instantiate job class {} for trigger {}",
              trigger.getJobDefinition().getJobClassName(),
              trigger.getId(),
              exception);
      return finish(null, false);
    }
    getContext()
        .pipeToSelf(CompletableFuture.supplyAsync(job::run, jobExecutor), Command.RunFinished::new);
    return this;
  }

  private Behavior<Command> onRunFinished(final Command.RunFinished command) {
    if (command.error() != null) {
      getContext()
          .getLog()
          .error("Job execution failed for trigger {}", trigger.getId(), command.error());
      return finish(null, false);
    }
    return finish(command.result(), true);
  }

  private Behavior<Command> onHeartbeat() {
    try {
      if (!triggerDefinitionRepository.heartbeat(trigger.getId())) {
        getContext()
            .getLog()
            .warn("Trigger {} lost its lease. Stopping execution.", trigger.getId());
        return Behaviors.stopped();
      }
    } catch (final RuntimeException exception) {
      getContext()
          .getLog()
          .error("Failed to update heartbeat for trigger {}", trigger.getId(), exception);
    }
    return this;
  }

  private boolean markRunning() {
    try {
      if (triggerDefinitionRepository.startTrigger(trigger.getId(), scheduledBy)) {
        return true;
      }
      getContext()
          .getLog()
          .warn(
              "Trigger {} is no longer queued for this worker. Not starting it.", trigger.getId());
    } catch (final RuntimeException exception) {
      getContext().getLog().error("Failed to start trigger {}", trigger.getId(), exception);
    }
    return false;
  }

  private Behavior<Command> finish(final JobResult jobResult, final boolean success) {
    final boolean reschedule = success && reschedule(jobResult);
    final Update update = reschedule ? rescheduleUpdate(trigger) : failedUpdate();
    try {
      triggerDefinitionRepository.update(trigger.getId(), update);
      if (reschedule) {
        worker.tell(new WorkerActor.Command.TriggerRescheduled(trigger));
      }
    } catch (final RuntimeException exception) {
      getContext().getLog().error("Failed to update trigger {}", trigger.getId(), exception);
    }
    worker.tell(new WorkerActor.Command.RunCompleted(trigger.getId()));
    return Behaviors.stopped();
  }

  private boolean reschedule(final JobResult jobResult) {
    final Optional<Instant> nextSchedule = SchedulerUtils.nextScheduledTime(trigger);
    trigger.setPreviousResult(CollectionUtils.nullSafeMutableMap(jobResult.data()));
    if (nextSchedule.isEmpty()) {
      return false;
    }
    final Instant scheduled = nextSchedule.get();
    trigger.setStatus(TriggerStatus.WAITING);
    trigger.setScheduledFor(scheduled.toEpochMilli());
    trigger.setDueAt(
        CronUtils.applyJitter(scheduled, Instant.now(), JITTER_FRACTION).toEpochMilli());
    return true;
  }

  private static Update rescheduleUpdate(final TriggerDefinition trigger) {
    return new Update(
        List.of(
            Operation.set(TriggerDefinition.FIELD_PREVIOUS_RESULT, trigger.getPreviousResult()),
            Operation.set(TriggerDefinition.FIELD_STATUS, trigger.getStatus()),
            Operation.set(TriggerDefinition.FIELD_SCHEDULED_FOR, trigger.getScheduledFor()),
            Operation.set(TriggerDefinition.FIELD_DUE_AT, trigger.getDueAt())));
  }

  private static Update failedUpdate() {
    return Update.of(Operation.set(TriggerDefinition.FIELD_STATUS, TriggerStatus.FAILED));
  }

  private static Job instantiate(final TriggerDefinition trigger)
      throws ReflectiveOperationException {
    final JobDefinition jobDefinition = trigger.getJobDefinition();
    final JobContext context =
        new JobContext(
            jobDefinition.getId(), jobDefinition.getPayload(), trigger.getPreviousResult());
    return Class.forName(jobDefinition.getJobClassName())
        .asSubclass(Job.class)
        .getConstructor(JobContext.class)
        .newInstance(context);
  }

  public interface Command extends Contextual, PekkoSerializable {

    record Start() implements Command {}

    record RunFinished(JobResult result, Throwable error) implements Command {}

    record Heartbeat() implements Command {}
  }
}
