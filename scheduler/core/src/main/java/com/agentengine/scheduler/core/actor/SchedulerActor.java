package com.agentengine.scheduler.core.actor;

import com.agentengine.scheduler.api.models.JobDefinition;
import com.agentengine.scheduler.api.models.TriggerDefinition;
import com.agentengine.scheduler.api.store.JobDefinitionRepository;
import com.agentengine.scheduler.api.store.TriggerDefinitionRepository;
import com.agentengine.scheduler.core.SchedulerConfigs;
import com.agentengine.scheduler.core.SchedulerUtils;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.EnvUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.pekko.PekkoSerializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;

/**
 * Cluster singleton that finds due triggers, takes them, and hands each to the {@link
 * JobRunnerActor} entity for its trigger.
 *
 * <p>Taking them is a batch compare-and-set that stamps this scheduler's id, followed by a read of
 * the rows carrying that id. The update reports how many rows it changed but not which, and the
 * read-back closes that gap: should a partition ever leave two singletons running, each dispatches
 * only the subset it actually won rather than both dispatching everything.
 *
 * <p>There is deliberately no recovery sweep for triggers stuck in QUEUED or RUNNING: without a
 * lease heartbeat a sweep cannot tell a long-running job from a dead node, and would re-dispatch
 * live work. See TODO.md.
 */
public final class SchedulerActor extends AbstractBehavior<SchedulerActor.Command> {

  private static final String TIMER_KEY = "scheduler";

  private final TriggerDefinitionRepository triggerDefinitionRepository;
  private final JobDefinitionRepository jobDefinitionRepository;
  private final JobRunnerActorFactory jobRunnerActorFactory;
  private final ConcurrencyLimiter concurrencyLimiter;
  private final ActorRef<JobRunnerActor.Response.TriggerFinished> schedulerCallbackActor;
  private final SchedulerConfigs schedulerConfigs;
  private final String schedulerId;

  public SchedulerActor(
      final ActorContext<Command> context,
      final TimerScheduler<Command> timers,
      final TriggerDefinitionRepository triggerDefinitionRepository,
      final JobDefinitionRepository jobDefinitionRepository,
      final JobRunnerActorFactory jobRunnerActorFactory,
      final ConcurrencyLimiter concurrencyLimiter,
      final SchedulerConfigs schedulerConfigs) {
    super(context);
    this.schedulerConfigs = schedulerConfigs;
    this.triggerDefinitionRepository = triggerDefinitionRepository;
    this.jobDefinitionRepository = jobDefinitionRepository;
    this.jobRunnerActorFactory = jobRunnerActorFactory;
    this.concurrencyLimiter = concurrencyLimiter;
    this.schedulerId = newSchedulerId();
    this.schedulerCallbackActor =
        context.messageAdapter(
            JobRunnerActor.Response.TriggerFinished.class, Command.RunFinished::new);
    timers.startTimerWithFixedDelay(
        TIMER_KEY, new Command.FetchTriggers(), schedulerConfigs.scanInterval());
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
        .onMessage(Command.FetchTriggers.class, this::onFetchTriggers)
        .onMessage(Command.DispatchResult.class, this::onDispatchResult)
        .onMessage(Command.RunFinished.class, this::onRunFinished)
        .build();
  }

  private Behavior<Command> onFetchTriggers(final Command.FetchTriggers command) {
    final long now = System.currentTimeMillis();
    concurrencyLimiter.evictExpired(now);
    try {
      fetch(now);
    } catch (final RuntimeException exception) {
      getContext().getLog().error("Scheduler scan failed", exception);
    }
    return this;
  }

  /**
   * The node either took the trigger or it did not. Capacity is handed straight back unless it was
   * taken, in which case it is held until the runner reports the run finished. A timed-out ask
   * means the node never answered, so nothing is running and the reservation is equally safe to
   * release.
   */
  private Behavior<Command> onDispatchResult(final Command.DispatchResult command) {
    if (command.failure() != null) {
      getContext()
          .getLog()
          .warn(
              "Trigger {} was not acknowledged by its runner. Holding capacity until TTL expires to prevent over-triggering.",
              command.triggerId(),
              command.failure());
      return this;
    }

    if (!command.accepted()) {
      getContext()
          .getLog()
          .debug(
              "Trigger {} rejected for lack of capacity; it will be picked up again",
              command.triggerId());
      concurrencyLimiter.release(command.tags(), command.triggerId());
      triggerDefinitionRepository.releaseTrigger(command.triggerId());
    }

    return this;
  }

  private Behavior<Command> onRunFinished(final Command.RunFinished command) {
    final JobRunnerActor.Response.TriggerFinished finished = command.finished();
    concurrencyLimiter.release(finished.tags(), finished.triggerId());
    return this;
  }

  private void fetch(final long now) {
    final List<TriggerDefinition> dueTriggers =
        triggerDefinitionRepository.findDueTriggers(schedulerConfigs.maxTriggersPerScan());
    if (CollectionUtils.isEmpty(dueTriggers)) {
      return;
    }
    final Map<String, JobDefinition> idVsJobs = fetchJobs(dueTriggers);

    final List<String> outdated = new ArrayList<>();
    final Map<String, List<String>> triggerIdVsTags = new LinkedHashMap<>();
    for (final TriggerDefinition trigger : dueTriggers) {
      if (isOutdated(trigger, idVsJobs.get(trigger.getJobDefinition().getId()))) {
        outdated.add(trigger.getId());
        continue;
      }
      final List<String> tags = SchedulerUtils.getJobTags(trigger.getJobDefinition());
      if (concurrencyLimiter.tryAcquire(tags, trigger.getId(), now)) {
        triggerIdVsTags.put(trigger.getId(), tags);
      }
    }
    try {
      triggerDefinitionRepository.cancelTriggers(outdated);
    } catch (final RuntimeException exception) {
      getContext().getLog().error("Failed to cancel outdated triggers", exception);
    }

    if (CollectionUtils.isEmpty(triggerIdVsTags)) {
      return;
    }
    try {
      triggerDefinitionRepository.queueTriggers(triggerIdVsTags.keySet(), schedulerId);
      dispatch(triggerIdVsTags);
    } catch (final RuntimeException exception) {
      // Capacity was reserved for triggers that will not now be dispatched. Without this the
      // reservations would sit until they expire, so one failed write would cost a tag its whole
      // quota for the length of the TTL.
      releaseAll(triggerIdVsTags);
      getContext().getLog().error("Failed to queue triggers for dispatch", exception);
    }
  }

  /**
   * Dispatches the triggers this scheduler actually took, and hands back the capacity it reserved
   * for any it did not. The read-back is narrowed to the ids attempted in this pass: a queued
   * trigger left over from an earlier one may already be sitting in a mailbox, and sending it again
   * would run the job twice.
   */
  private void dispatch(final Map<String, List<String>> triggerIdVsTags) {
    final Map<String, TriggerDefinition> taken = new HashMap<>();
    for (final TriggerDefinition trigger : triggerDefinitionRepository.findQueuedBy(schedulerId)) {
      if (triggerIdVsTags.containsKey(trigger.getId())) {
        taken.put(trigger.getId(), trigger);
      }
    }
    for (final Entry<String, List<String>> entry : triggerIdVsTags.entrySet()) {
      if (!taken.containsKey(entry.getKey())) {
        concurrencyLimiter.release(entry.getValue(), entry.getKey());
      }
    }
    taken
        .values()
        .forEach(
            takenTrigger -> {
              try {
                trigger(takenTrigger, triggerIdVsTags.get(takenTrigger.getId()));
              } catch (final RuntimeException exception) {
                getContext()
                    .getLog()
                    .error(
                        "Failed to dispatch trigger {} for job {}",
                        takenTrigger.getId(),
                        takenTrigger.getJobDefinition().getId(),
                        exception);
              }
            });
  }

  private void releaseAll(final Map<String, List<String>> triggerIdVsTags) {
    triggerIdVsTags.forEach((triggerId, tags) -> concurrencyLimiter.release(tags, triggerId));
  }

  private void trigger(final TriggerDefinition trigger, final List<String> tags) {
    getContext()
        .ask(
            JobRunnerActor.Response.RunAck.class,
            jobRunnerActorFactory.entityRef(trigger.getId()),
            schedulerConfigs.dispatchAskTimeout(),
            replyTo ->
                new JobRunnerActor.Command.RunTrigger(trigger, replyTo, schedulerCallbackActor),
            (accepted, failure) ->
                new Command.DispatchResult(
                    trigger.getId(), tags, accepted != null && accepted.accepted(), failure));
  }

  private Map<String, JobDefinition> fetchJobs(final List<TriggerDefinition> dueTriggers) {
    final Set<String> jobIds = new LinkedHashSet<>();
    for (final TriggerDefinition trigger : dueTriggers) {
      jobIds.add(trigger.getJobDefinition().getId());
    }
    return jobIds.isEmpty() ? Map.of() : jobDefinitionRepository.findByIds(jobIds);
  }

  /**
   * A newer job version normally means a newer trigger was written, since scheduling a job writes
   * both. It is not an invariant: the two are separate writes, so a failure between them leaves the
   * version ahead of any trigger. Recovery is the caller retrying the schedule, which writes a
   * trigger at a version higher again — not anything this check does.
   */
  private static boolean isOutdated(
      final TriggerDefinition trigger, final JobDefinition jobDefinition) {
    if (jobDefinition == null) {
      return true;
    }
    return jobDefinition.getVersion() > trigger.getJobDefinition().getVersion();
  }

  /**
   * Unique per singleton incarnation rather than per host: a scheduler that is replaced must not
   * mistake rows stamped by its predecessor for ones it took itself.
   */
  private static String newSchedulerId() {
    final String host = EnvUtils.getHostname();
    return (StringUtils.isBlank(host) ? "scheduler" : host) + ":" + UUID.randomUUID();
  }

  public interface Command extends PekkoSerializable {

    record FetchTriggers() implements Command {}

    /** Outcome of the ask that offered a trigger to its runner. */
    record DispatchResult(String triggerId, List<String> tags, boolean accepted, Throwable failure)
        implements Command {}

    record RunFinished(JobRunnerActor.Response.TriggerFinished finished) implements Command {}
  }
}
