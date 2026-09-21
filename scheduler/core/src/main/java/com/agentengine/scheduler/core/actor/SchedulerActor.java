package com.agentengine.scheduler.core.actor;

import com.agentengine.scheduler.api.models.JobDefinition;
import com.agentengine.scheduler.api.models.TriggerDefinition;
import com.agentengine.scheduler.api.models.TriggerStatus;
import com.agentengine.scheduler.api.store.JobDefinitionRepository;
import com.agentengine.scheduler.api.store.TriggerDefinitionRepository;
import com.agentengine.scheduler.core.SchedulerConfigs;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.collections.DeficitRoundRobinQueue;
import com.agentengine.util.pekko.PekkoSerializable;
import java.time.Duration;
import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;

/**
 * Cluster singleton that keeps the due triggers in fair order and hands them to the {@link
 * WorkerActor}s that ask for work.
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

  public static final List<String> RECONCILE_JOB_FIELDS =
          List.of(
                  TriggerDefinition.FIELD_JOB_DEFINITION + "." + JobDefinition.FIELD_USER_CONTEXT,
                  TriggerDefinition.FIELD_JOB_DEFINITION + "." + BaseEntity.FIELD_TAGS,
                  TriggerDefinition.FIELD_JOB_DEFINITION + "." + JobDefinition.FIELD_JOB_CLASS_NAME);

  private static final String TIMER_KEY = "scheduler";
  private static final String RECONCILE_TIMER_KEY = "reconciliation";

  private final TriggerDefinitionRepository triggerDefinitionRepository;
  private final JobDefinitionRepository jobDefinitionRepository;
  private final SchedulerConfigs schedulerConfigs;
  private final TimerScheduler<Command> timers;
  private DeficitRoundRobinQueue<Integer, TriggerDefinition> dueTriggers = new DeficitRoundRobinQueue<>(_ -> 1);
  private boolean initialized;

  public SchedulerActor(
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
    context.getSelf().tell(new Command.Reconcile());
    timers.startTimerWithFixedDelay(
        RECONCILE_TIMER_KEY, new Command.Reconcile(), schedulerConfigs.reconcileInterval());
    timers.startTimerWithFixedDelay(
        TIMER_KEY, new Command.FetchTriggers(), schedulerConfigs.scanInterval());
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
    try {
      initialized = true;
    } catch (final RuntimeException exception) {
      getContext().getLog().error("Failed to reconcile in-flight triggers", exception);
    }
    return this;
  }

  private Behavior<Command> onRequestWork(final Command.RequestWork command) {
    command.replyTo().tell(new Response.WorkGranted(getWork(command)));
    return this;
  }

  private Behavior<Command> onRunFinished(final Command.RunFinished command) {
    return this;
  }

  private Behavior<Command> onJobScheduled(final Command.JobScheduled command) {
    try {
      final TriggerDefinition trigger = triggerDefinitionRepository.findById(command.triggerId());
      if (trigger == null || trigger.getStatus() != TriggerStatus.WAITING) {
        return this;
      }
      final long delayMs = trigger.getDueAt() - System.currentTimeMillis();
      if (delayMs <= 0) {
        dueTriggers.enqueue(
            trigger.getJobDefinition().getUserContext().customerId(), trigger, 1, TenantQueue::new);
      } else {
        timers.startSingleTimer(
            command.triggerId(), new Command.TriggerDue(command.triggerId()), Duration.ofMillis(delayMs));
      }
    } catch (final RuntimeException exception) {
      getContext().getLog().error("Failed to process JobScheduled", exception);
    }
    return this;
  }

  private Behavior<Command> onTriggerDue(final Command.TriggerDue command) {
    try {
      final TriggerDefinition trigger = triggerDefinitionRepository.findById(command.triggerId());
      if (trigger != null && trigger.getStatus() == TriggerStatus.WAITING) {
        dueTriggers.enqueue(
            trigger.getJobDefinition().getUserContext().customerId(), trigger, 1, TenantQueue::new);
      }
    } catch (final RuntimeException exception) {
      getContext().getLog().error("Failed to process TriggerDue", exception);
    }
    return this;
  }

  private void fetch() {
    if (!initialized) {
      return;
    }
    final List<TriggerDefinition> due =
        triggerDefinitionRepository.findDueTriggers(schedulerConfigs.maxTriggersPerScan());
    if (CollectionUtils.isEmpty(due)) {
      dueTriggers = new DeficitRoundRobinQueue<>(_ -> 1);
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
    dueTriggers = new DeficitRoundRobinQueue<>(_ -> 1);
    for (final TriggerDefinition trigger : current) {
      dueTriggers.enqueue(
          trigger.getJobDefinition().getUserContext().customerId(), trigger, 1, TenantQueue::new);
    }
    try {
      triggerDefinitionRepository.cancelTriggers(outdated);
    } catch (final RuntimeException exception) {
      getContext().getLog().error("Failed to cancel outdated triggers", exception);
    }
  }

  private List<TriggerDefinition> getWork(final Command.RequestWork command) {
    if (!initialized) {
      return List.of();
    }
    final List<TriggerDefinition> chosen = new ArrayList<>();
    while (chosen.size() < command.slots()) {
      final TriggerDefinition trigger = dueTriggers.poll();
      if (trigger == null) {
        break;
      }
      chosen.add(trigger);
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
    return jobIds.isEmpty() ? Map.of() : jobDefinitionRepository.findByIds(jobIds, List.of(BaseEntity.FIELD_VERSION), null);
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

  private static final class TenantQueue extends AbstractQueue<TriggerDefinition> {
    private final DeficitRoundRobinQueue<String, TriggerDefinition> tagQueue =
        new DeficitRoundRobinQueue<>(_ -> 1);
    private TriggerDefinition head;

    @Override
    public boolean offer(final TriggerDefinition trigger) {
      final String tag = CollectionUtils.isEmpty(trigger.getJobDefinition().getTags())
          ? "default"
          : trigger.getJobDefinition().getTags().getFirst();
      tagQueue.enqueue(tag, trigger, 1, () -> new PriorityQueue<>(Comparator.comparing(TriggerDefinition::getDueAt)));
      if (head == null) {
        head = tagQueue.poll();
      }
      return true;
    }

    @Override
    public TriggerDefinition poll() {
      final TriggerDefinition res = head;
      head = tagQueue.poll();
      return res;
    }

    @Override
    public TriggerDefinition peek() {
      return head;
    }

    @Override
    public int size() {
      return head != null ? 1 : 0;
    }

    @Override
    public Iterator<TriggerDefinition> iterator() {
      return null;
    }
  }

  public interface Command extends PekkoSerializable {

    record FetchTriggers() implements Command {}

    record Reconcile() implements Command {}

    record RequestWork(String workerId, int slots, ActorRef<Response.WorkGranted> replyTo)
        implements Command {}

    record RunFinished(String triggerId) implements Command {}

    record JobScheduled(String triggerId) implements Command {}

    record TriggerDue(String triggerId) implements Command {}
  }

  public interface Response extends PekkoSerializable {

    record WorkGranted(List<TriggerDefinition> triggers) implements Response {}
  }
}
