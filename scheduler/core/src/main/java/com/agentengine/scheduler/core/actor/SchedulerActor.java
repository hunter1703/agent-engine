package com.agentengine.scheduler.core.actor;

import com.agentengine.scheduler.api.models.JobDefinition;
import com.agentengine.scheduler.api.models.TriggerDefinition;
import com.agentengine.scheduler.api.store.JobDefinitionRepository;
import com.agentengine.scheduler.api.store.TriggerDefinitionRepository;
import com.agentengine.scheduler.core.SchedulerConfigs;
import com.agentengine.scheduler.core.SchedulerUtils;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.collections.FairQueue;
import com.agentengine.util.context.Context;
import com.agentengine.util.context.Contextual;
import com.agentengine.util.context.UserContext;
import com.agentengine.util.pekko.PekkoSerializable;
import com.agentengine.util.pekko.actor.ContextualInterceptor;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;

/**
 * Cluster singleton that keeps the due triggers in fair order and hands them to the {@link
 * WorkerActor}s that ask for work.
 *
 * <p>Fairness is two levels of Deficit Round Robin: across tenants, then — within a tenant — across
 * job tags. A trigger's tag-level key is whichever of its own tags currently has the most due
 * triggers of that same tenant waiting on it (its busiest tag, including its job class name, which
 * is always an implicit tag), so a job with several tags is throttled by the most contended one it
 * belongs to rather than able to slip through on its least contended.
 *
 * <p>Handing them out is a batch compare-and-set that stamps the asking worker's id, followed by a
 * read of the rows carrying that id. The update reports how many rows it changed but not which, and
 * the read-back closes that gap: should a partition ever leave two singletons running, each grants
 * only the subset it actually won rather than both granting everything.
 *
 * <p>Recovering triggers stuck in QUEUED or RUNNING after a dead node is handled separately, by
 * {@link TriggerReconcilerActor}, which uses each trigger's heartbeat lease to tell a long-running
 * job from an abandoned one.
 */
public final class SchedulerActor extends AbstractBehavior<SchedulerActor.Command> {

  private static final String TIMER_KEY = "scheduler";
  private static final String RECONCILE_TIMER_KEY = "reconciliation";

  private final TriggerDefinitionRepository triggerDefinitionRepository;
  private final JobDefinitionRepository jobDefinitionRepository;
  private final SchedulerConfigs schedulerConfigs;
  private final TimerScheduler<Command> timers;
  private FairQueue<Integer, TaggedTrigger> dueTriggers;
  private boolean initialized;

  private SchedulerActor(
      final ActorContext<Command> context,
      final TimerScheduler<Command> timers,
      final TriggerDefinitionRepository triggerDefinitionRepository,
      final JobDefinitionRepository jobDefinitionRepository,
      final SchedulerConfigs schedulerConfigs) {
    super(context);
    this.timers = timers;
    this.schedulerConfigs = schedulerConfigs;
    this.triggerDefinitionRepository = triggerDefinitionRepository;
    this.jobDefinitionRepository = jobDefinitionRepository;
    this.dueTriggers = emptyQueue();
    context.getSelf().tell(new Command.Reconcile());
    timers.startTimerWithFixedDelay(
        RECONCILE_TIMER_KEY, new Command.Reconcile(), schedulerConfigs.reconcileInterval());
    timers.startTimerWithFixedDelay(
        TIMER_KEY, new Command.FetchTriggers(), schedulerConfigs.scanInterval());
  }

  public static Behavior<Command> create(
      final TriggerDefinitionRepository triggerDefinitionRepository,
      final JobDefinitionRepository jobDefinitionRepository,
      final SchedulerConfigs schedulerConfigs) {
    return Behaviors.intercept(
        () ->
            new ContextualInterceptor<>(
                Command.class, new Context(UUID.randomUUID().toString(), UserContext.SYSTEM)),
        Behaviors.setup(
            context ->
                Behaviors.withTimers(
                    timers ->
                        new SchedulerActor(
                            context,
                            timers,
                            triggerDefinitionRepository,
                            jobDefinitionRepository,
                            schedulerConfigs))));
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
        .onMessage(Command.FetchTriggers.class, this::onFetchTriggers)
        .onMessage(Command.Reconcile.class, this::onReconcile)
        .onMessage(Command.RequestWork.class, this::onRequestWork)
        .onMessage(Command.RunFinished.class, this::onRunFinished)
        .onMessage(Command.JobScheduled.class, this::onJobScheduled)
        .onMessage(Command.TriggerDue.class, this::onTriggerDue)
        .build();
  }

  private Behavior<Command> onFetchTriggers(final Command.FetchTriggers command) {
    try {
      fetch();
    } catch (final RuntimeException exception) {
      getContext().getLog().error("Scheduler scan failed", exception);
    }
    return this;
  }

  private Behavior<Command> onReconcile(final Command.Reconcile command) {
    initialized = true;
    return this;
  }

  private Behavior<Command> onRequestWork(final Command.RequestWork command) {
    command.replyTo().tell(new Response.WorkGranted(getWork(command)));
    return this;
  }

  private Behavior<Command> onRunFinished(final Command.RunFinished command) {
    return this;
  }

  /**
   * The caller already holds a just-written, up-to-date {@link TriggerDefinition} (freshly saved,
   * or freshly rescheduled after a run) — no need to re-fetch it here. If it turns out to be stale
   * by the time a worker tries to claim it (say, canceled in between), the batch compare-and-set in
   * {@link #queue} simply won't match it; enqueueing a stale trigger costs a wasted slot offer, not
   * incorrect execution.
   */
  private Behavior<Command> onJobScheduled(final Command.JobScheduled command) {
    try {
      final TriggerDefinition trigger = command.trigger();
      final long delayMs = trigger.getDueAt() - System.currentTimeMillis();
      if (delayMs <= 0) {
        enqueueSingle(trigger);
      } else {
        timers.startSingleTimer(
            trigger.getId(), new Command.TriggerDue(trigger), Duration.ofMillis(delayMs));
      }
    } catch (final RuntimeException exception) {
      getContext().getLog().error("Failed to process JobScheduled", exception);
    }
    return this;
  }

  private Behavior<Command> onTriggerDue(final Command.TriggerDue command) {
    try {
      enqueueSingle(command.trigger());
    } catch (final RuntimeException exception) {
      getContext().getLog().error("Failed to process TriggerDue", exception);
    }
    return this;
  }

  /**
   * Enqueues one trigger outside a full scan, where there is no batch to weigh its tags' busyness
   * against — it goes under its first tag (its job class name, absent any declared ones).
   *
   * <p>If the tenant or tag is already at capacity, the trigger is dropped from this in-memory
   * queue rather than held: the database is authoritative and the trigger stays WAITING there, so
   * the next periodic {@link #fetch} picks it up regardless.
   */
  private void enqueueSingle(final TriggerDefinition trigger) {
    final String tag = SchedulerUtils.getJobTags(trigger).getFirst();
    final boolean enqueued =
        dueTriggers.enqueue(trigger.getCustomerId(), new TaggedTrigger(trigger, tag));
    if (!enqueued) {
      getContext()
          .getLog()
          .debug(
              "Dropping trigger_id={} from the event-driven queue; at capacity, next scan will"
                  + " pick it up",
              trigger.getId());
    }
  }

  private void fetch() {
    if (!initialized) {
      return;
    }
    final List<TriggerDefinition> due =
        triggerDefinitionRepository.findDueTriggers(schedulerConfigs.maxTriggersPerScan());
    if (CollectionUtils.isEmpty(due)) {
      dueTriggers = emptyQueue();
      return;
    }
    final Map<String, JobDefinition> idVsJobs = fetchJobs(due);

    final List<String> outdated = new ArrayList<>();
    final List<TriggerDefinition> current = new ArrayList<>();
    for (final TriggerDefinition trigger : due) {
      if (isOutdated(trigger, idVsJobs.get(trigger.getJobDefinition().getId()))) {
        outdated.add(trigger.getId());
      } else {
        current.add(trigger);
      }
    }
    dueTriggers = buildQueue(current);
    try {
      triggerDefinitionRepository.cancelTriggers(outdated);
    } catch (final RuntimeException exception) {
      getContext().getLog().error("Failed to cancel outdated triggers", exception);
    }
  }

  private FairQueue<Integer, TaggedTrigger> buildQueue(final List<TriggerDefinition> current) {
    final Map<Integer, Map<String, Integer>> tenantVsTagCounts = tagCountsByTenant(current);
    final FairQueue<Integer, TaggedTrigger> queue = emptyQueue();
    for (final TriggerDefinition trigger : current) {
      final Map<String, Integer> tagCounts = tenantVsTagCounts.get(trigger.getCustomerId());
      queue.enqueue(
          trigger.getCustomerId(), new TaggedTrigger(trigger, busiestTag(trigger, tagCounts)));
    }
    return queue;
  }

  /**
   * Caps each tenant's total in-memory backlog at {@code maxTriggersPerScan} and, within that, each
   * of the tenant's own tags at {@code maxTriggersPerTagPerScan} — otherwise one job class
   * rescheduling itself repeatedly for one tenant could consume that tenant's entire budget.
   */
  private FairQueue<Integer, TaggedTrigger> emptyQueue() {
    return new FairQueue<>(
        null, _ -> 1, _ -> 1, _ -> tagFairQueue(), _ -> schedulerConfigs.maxTriggersPerScan());
  }

  private FairQueue<String, TaggedTrigger> tagFairQueue() {
    return new FairQueue<>(
        TaggedTrigger::tag,
        _ -> 1,
        _ -> 1,
        _ -> new PriorityQueue<>(Comparator.comparing(tagged -> tagged.trigger().getDueAt())),
        _ -> schedulerConfigs.maxTriggersPerTagPerScan());
  }

  private static Map<Integer, Map<String, Integer>> tagCountsByTenant(
      final List<TriggerDefinition> current) {
    final Map<Integer, Map<String, Integer>> tenantVsTagCounts = new HashMap<>();
    for (final TriggerDefinition trigger : current) {
      final Map<String, Integer> tagCounts =
          tenantVsTagCounts.computeIfAbsent(trigger.getCustomerId(), _ -> new HashMap<>());
      for (final String tag : SchedulerUtils.getJobTags(trigger)) {
        tagCounts.merge(tag, 1, Integer::sum);
      }
    }
    return tenantVsTagCounts;
  }

  private static String busiestTag(
      final TriggerDefinition trigger, final Map<String, Integer> tagCounts) {
    String busiest = null;
    int busiestCount = -1;
    for (final String tag : SchedulerUtils.getJobTags(trigger)) {
      final int count = tagCounts.getOrDefault(tag, 0);
      if (count > busiestCount) {
        busiest = tag;
        busiestCount = count;
      }
    }
    return busiest;
  }

  private List<TriggerDefinition> getWork(final Command.RequestWork command) {
    if (!initialized) {
      return List.of();
    }
    final List<TriggerDefinition> chosen = new ArrayList<>();
    while (chosen.size() < command.slots()) {
      final TaggedTrigger tagged = dueTriggers.poll();
      if (tagged == null) {
        break;
      }
      chosen.add(tagged.trigger());
    }
    if (CollectionUtils.isEmpty(chosen)) {
      return List.of();
    }
    List<TriggerDefinition> queued = List.of();
    try {
      queued = queue(chosen, command.workerId());
    } catch (final RuntimeException exception) {
      getContext().getLog().error("Failed to queue triggers for a worker", exception);
    }
    return queued;
  }

  private List<TriggerDefinition> queue(
      final List<TriggerDefinition> chosen, final String workerId) {
    return triggerDefinitionRepository.queueTriggers(chosen, workerId);
  }

  private Map<String, JobDefinition> fetchJobs(final List<TriggerDefinition> due) {
    final Set<String> jobIds = new LinkedHashSet<>();
    for (final TriggerDefinition trigger : due) {
      jobIds.add(trigger.getJobDefinition().getId());
    }
    return jobIds.isEmpty()
        ? Map.of()
        : jobDefinitionRepository.findByIds(jobIds, List.of(BaseEntity.FIELD_VERSION), null);
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

  private record TaggedTrigger(TriggerDefinition trigger, String tag) {}

  public interface Command extends Contextual, PekkoSerializable {

    record FetchTriggers() implements Command {}

    record Reconcile() implements Command {}

    record RequestWork(String workerId, int slots, ActorRef<Response.WorkGranted> replyTo)
        implements Command {}

    record RunFinished(String triggerId) implements Command {}

    record JobScheduled(TriggerDefinition trigger) implements Command {}

    record TriggerDue(TriggerDefinition trigger) implements Command {}
  }

  public interface Response extends PekkoSerializable {

    record WorkGranted(List<TriggerDefinition> triggers) implements Response {}
  }
}
