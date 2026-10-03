package com.agentengine.scheduler.core.runner;

import static com.agentengine.scheduler.core.SchedulerUtils.JITTER_FRACTION;

import com.agentengine.scheduler.api.models.JobDefinition;
import com.agentengine.scheduler.api.models.TriggerDefinition;
import com.agentengine.scheduler.api.models.TriggerStatus;
import com.agentengine.scheduler.api.runner.SchedulerService;
import com.agentengine.scheduler.core.CronUtils;
import com.agentengine.scheduler.core.SchedulerActorFactory;
import com.agentengine.scheduler.core.SchedulerUtils;
import com.agentengine.scheduler.core.actor.SchedulerActor;
import com.agentengine.scheduler.core.store.JobDefinitionRepository;
import com.agentengine.scheduler.core.store.TriggerDefinitionRepository;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.context.Context;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.Optional;

@Singleton
@Unremovable
public class SchedulerServiceImpl implements SchedulerService {

  private final JobDefinitionRepository jobDefinitionRepository;
  private final TriggerDefinitionRepository triggerDefinitionRepository;
  private final SchedulerActorFactory schedulerActorFactory;

  @Inject
  public SchedulerServiceImpl(
      final JobDefinitionRepository jobDefinitionRepository,
      final TriggerDefinitionRepository triggerDefinitionRepository,
      final SchedulerActorFactory schedulerActorFactory) {
    this.jobDefinitionRepository = jobDefinitionRepository;
    this.triggerDefinitionRepository = triggerDefinitionRepository;
    this.schedulerActorFactory = schedulerActorFactory;
  }

  @Override
  public String schedule(final JobDefinition jobDefinition) {
    saveTrigger(jobDefinitionRepository.save(asCaller(jobDefinition)));
    return jobDefinition.getId();
  }

  @Override
  public String scheduleIgnoringVersion(final JobDefinition jobDefinition) {
    saveTrigger(jobDefinitionRepository.saveIgnoringVersion(asCaller(jobDefinition)));
    return jobDefinition.getId();
  }

  @Override
  public JobDefinition getJob(final String jobId) {
    return jobDefinitionRepository.findById(jobId);
  }

  @Override
  public PaginatedResult<JobDefinition> findJobs(final Query query) {
    return jobDefinitionRepository.findByQuery(query);
  }

  @Override
  public boolean cancelJob(final String jobId) {
    if (!jobDefinitionRepository.deleteByIdIgnoringVersion(jobId)) {
      return false;
    }
    triggerDefinitionRepository.cancelAllJobTriggers(jobId);
    return true;
  }

  /** The job, set to run in the current customer, as the current caller. */
  private static JobDefinition asCaller(final JobDefinition jobDefinition) {
    final Context context = Context.require();
    jobDefinition.setCustomerId(context.customerId());
    jobDefinition.setCaller(context.caller().toString());
    return jobDefinition;
  }

  /**
   * Retiring the previous triggers is housekeeping, not correctness: the scheduler already refuses
   * to dispatch a trigger whose job has moved on. Doing it here keeps the collection from
   * accumulating a queued row per reschedule — each of which would otherwise sit until its own fire
   * time before the scheduler cleared it — and makes a cancellation take effect immediately rather
   * than at the next occurrence. A failure of this write is therefore harmless.
   */
  private void saveTrigger(final JobDefinition jobDefinition) {
    triggerDefinitionRepository.cancelAllJobTriggers(jobDefinition.getId());
    final TriggerDefinition triggerDefinition = new TriggerDefinition();
    triggerDefinition.setJobDefinition(jobDefinition);
    final Optional<Instant> scheduled = SchedulerUtils.nextScheduledTime(triggerDefinition);
    if (scheduled.isEmpty()) {
      return;
    }
    triggerDefinition.setStatus(TriggerStatus.WAITING);
    triggerDefinition.setScheduledFor(scheduled.get().toEpochMilli());
    triggerDefinition.setDueAt(
        CronUtils.applyJitter(scheduled.get(), Instant.now(), JITTER_FRACTION).toEpochMilli());
    final TriggerDefinition savedTrigger = triggerDefinitionRepository.save(triggerDefinition);
    schedulerActorFactory
        .getSchedulerRef()
        .tell(new SchedulerActor.Command.JobScheduled(savedTrigger));
  }
}
