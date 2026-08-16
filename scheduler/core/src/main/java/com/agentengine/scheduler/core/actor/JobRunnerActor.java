package com.agentengine.scheduler.core.actor;

import static com.agentengine.scheduler.core.SchedulerUtils.JITTER_FRACTION;

import com.agentengine.scheduler.api.models.JobDefinition;
import com.agentengine.scheduler.api.models.TriggerDefinition;
import com.agentengine.scheduler.api.models.TriggerStatus;
import com.agentengine.scheduler.api.runner.Job;
import com.agentengine.scheduler.api.runner.JobContext;
import com.agentengine.scheduler.api.runner.JobResult;
import com.agentengine.scheduler.api.store.TriggerDefinitionRepository;
import com.agentengine.scheduler.core.SchedulerUtils;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.CronUtils;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.pekko.PekkoSerializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

/**
 * Runs the trigger it is named after. Sharded by trigger id, so this entity id serves that trigger
 * for as long as it exists: every occurrence of a recurring schedule is routed back here.
 *
 * <p>An incarnation handles a single run and then stops, so the next occurrence arrives at a fresh
 * instance — the schedule outlives the actor, not the other way round. Within a run the behaviour
 * changes from idle to running, which is what makes a second dispatch for a run already under way
 * decline rather than start the job twice.
 */
public class JobRunnerActor extends AbstractBehavior<JobRunnerActor.Command> {

  public static final EntityTypeKey<Command> TYPE_KEY =
      EntityTypeKey.create(Command.class, "JobRunner");

  /** The node-wide cap is one bucket: it protects the pod, not a particular job class. */
  private static final List<String> NODE_PERMIT = List.of("node");

  private final String entityId;
  private final TriggerDefinitionRepository triggerDefinitionRepository;
  private final ConcurrencyLimiter nodeLimiter;
  private final Executor jobExecutor;
  private final TimerScheduler<Command> timers;
  private final Duration heartbeatInterval;

  private static final String HEARTBEAT_TIMER_KEY = "heartbeat";

  public JobRunnerActor(
      final ActorContext<Command> context,
      final TimerScheduler<Command> timers,
      final String entityId,
      final TriggerDefinitionRepository triggerDefinitionRepository,
      final ConcurrencyLimiter nodeLimiter,
      final Executor jobExecutor,
      final Duration heartbeatInterval) {
    super(context);
    this.timers = timers;
    this.entityId = entityId;
    this.triggerDefinitionRepository = triggerDefinitionRepository;
    this.nodeLimiter = nodeLimiter;
    this.jobExecutor = jobExecutor;
    this.heartbeatInterval = heartbeatInterval;
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
        .onMessage(Command.RunTrigger.class, this::onRunTrigger)
        .onSignal(PostStop.class, this::onPostStop)
        .build();
  }

  private Behavior<Command> onRunTrigger(final Command.RunTrigger command) {
    final TriggerDefinition trigger = command.triggerDefinition();

    if (!nodeLimiter.tryAcquire(NODE_PERMIT, entityId, System.currentTimeMillis())) {
      return reject(command);
    }
    if (!updateTrigger(
        Update.of(
            Operation.set(TriggerDefinition.FIELD_STATUS, TriggerStatus.RUNNING),
            Operation.set(TriggerDefinition.FIELD_LAST_HEARTBEAT, System.currentTimeMillis())))) {
      return reject(command);
    }

    timers.startTimerWithFixedDelay(
        HEARTBEAT_TIMER_KEY, new Command.Heartbeat(), heartbeatInterval);

    accept(command);

    final Job job;
    try {
      job = instantiate(trigger);
    } catch (final ReflectiveOperationException | RuntimeException exception) {
      getContext()
          .getLog()
          .error(
              "Could not instantiate job class {} for trigger {}",
              trigger.getJobDefinition().getJobClassName(),
              entityId,
              exception);
      return fail(command);
    }

    getContext()
        .pipeToSelf(CompletableFuture.supplyAsync(job::run, jobExecutor), Command.RunFinished::new);
    return running(command);
  }

  private void accept(final Command.RunTrigger command) {
    command.replyTo().tell(new Response.RunAck(true));
  }

  private Behavior<Command> reject(final Command.RunTrigger command) {
    command.replyTo().tell(new Response.RunAck(false));
    return Behaviors.stopped();
  }

  /** Accepted: from here only this run's outcome matters, and a further dispatch is declined. */
  private Receive<Command> running(final Command.RunTrigger runTrigger) {
    return newReceiveBuilder()
        .onMessage(Command.RunFinished.class, finished -> onRunFinished(runTrigger, finished))
        .onMessage(Command.Heartbeat.class, _ -> onHeartbeat())
        .onMessage(Command.RunTrigger.class, this::declineWhileRunning)
        .onSignal(PostStop.class, this::onPostStop)
        .build();
  }

  private Behavior<Command> onHeartbeat() {
    try {
      if (!triggerDefinitionRepository.heartbeat(entityId)) {
        getContext().getLog().warn("Trigger {} lost its lease. Stopping execution.", entityId);
        return Behaviors.stopped();
      }
    } catch (final RuntimeException exception) {
      getContext().getLog().error("Failed to update heartbeat for trigger {}", entityId, exception);
    }
    return Behaviors.same();
  }

  private Behavior<Command> onRunFinished(
      final Command.RunTrigger runTrigger, final Command.RunFinished runFinished) {
    if (runFinished.error() != null) {
      getContext()
          .getLog()
          .error("Job execution failed for trigger {}", entityId, runFinished.error());
      return fail(runTrigger);
    }
    return success(runTrigger, runFinished.result());
  }

  /**
   * The scheduler only dispatches waiting triggers, so this means one slipped through — a stale
   * message, or a redelivery. Declining it costs the scheduler nothing but its reserved capacity,
   * whereas leaving it unhandled would make it wait out the ask timeout for the same answer.
   */
  private Behavior<Command> declineWhileRunning(final Command.RunTrigger command) {
    getContext()
        .getLog()
        .warn("Trigger {} dispatched while its run is already in progress", entityId);
    command.replyTo().tell(new Response.RunAck(false));
    return Behaviors.same();
  }

  /**
   * Carries the run's result forward and arms the next occurrence, or retires an exhausted
   * schedule.
   */
  private Update rescheduleUpdate(final TriggerDefinition trigger, final JobResult result) {
    final List<Operation> operations = new ArrayList<>();
    final Map<String, Object> jobResult = CollectionUtils.nullSafeMutableMap(result.data());
    operations.add(Operation.set(TriggerDefinition.FIELD_PREVIOUS_RESULT, jobResult));
    final Optional<Instant> nextSchedule = SchedulerUtils.nextScheduledTime(trigger);
    if (nextSchedule.isEmpty()) {
      operations.add(Operation.set(TriggerDefinition.FIELD_STATUS, TriggerStatus.SUCCEEDED));
      return new Update(operations);
    }
    final Instant scheduled = nextSchedule.get();
    operations.add(Operation.set(TriggerDefinition.FIELD_STATUS, TriggerStatus.WAITING));
    operations.add(Operation.set(TriggerDefinition.FIELD_SCHEDULED_FOR, scheduled.toEpochMilli()));
    operations.add(
        Operation.set(
            TriggerDefinition.FIELD_DUE_AT,
            CronUtils.applyJitter(scheduled, Instant.now(), JITTER_FRACTION).toEpochMilli()));
    return new Update(operations);
  }

  private Behavior<Command> fail(final Command.RunTrigger runTrigger) {
    return finish(runTrigger, null, false);
  }

  private Behavior<Command> success(final Command.RunTrigger runTrigger, final JobResult result) {
    return finish(runTrigger, result, true);
  }

  private Behavior<Command> finish(
      final Command.RunTrigger runTrigger, final JobResult jobResult, final boolean success) {
    TriggerDefinition triggerDef = runTrigger.triggerDefinition();
    final Update update = success ? rescheduleUpdate(triggerDef, jobResult) : failedUpdate();
    updateTrigger(update);
    runTrigger
        .finishedCallbackActor()
        .tell(
            new Response.TriggerFinished(
                entityId, SchedulerUtils.getJobTags(triggerDef.getJobDefinition())));
    return Behaviors.stopped();
  }

  /**
   * The node permit is returned on stop rather than at each exit, so it also comes back when the
   * entity is passivated, killed during shutdown, or fails outside the run. Releasing one that was
   * never taken — the rejection path — is a no-op.
   */
  private Behavior<Command> onPostStop(final PostStop signal) {
    nodeLimiter.release(NODE_PERMIT, entityId);
    return Behaviors.same();
  }

  private boolean updateTrigger(final Update update) {
    try {
      triggerDefinitionRepository.update(entityId, update);
      return true;
    } catch (final RuntimeException exception) {
      getContext().getLog().error("Failed to update trigger {}", entityId, exception);
      return false;
    }
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

  private static Update failedUpdate() {
    return Update.of(Operation.set(TriggerDefinition.FIELD_STATUS, TriggerStatus.FAILED));
  }

  public interface Command extends PekkoSerializable {

    record RunTrigger(
        TriggerDefinition triggerDefinition,
        ActorRef<Response.RunAck> replyTo,
        ActorRef<Response.TriggerFinished> finishedCallbackActor)
        implements Command {}

    /** The job returned or threw; sent by the entity to itself when execution completes. */
    record RunFinished(JobResult result, Throwable error) implements Command {}

    /** Internal periodic tick to renew lease in the database. */
    record Heartbeat() implements Command {}
  }

  public interface Response extends PekkoSerializable {

    /**
     * Answer to the scheduler's ask: whether this node took the trigger. Sent before any work
     * starts, so the scheduler's timeout on it can be short.
     */
    record RunAck(boolean accepted) implements Response {}

    /**
     * Sent once the run has settled, so the scheduler can free the capacity it reserved. Delivery
     * is best-effort — a node that dies mid-run sends nothing, which is what the scheduler's permit
     * expiry covers.
     */
    record TriggerFinished(String triggerId, List<String> tags) implements Response {}
  }
}
