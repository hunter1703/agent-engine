package com.agentengine.scheduler.core.runner;

import static com.agentengine.scheduler.core.SchedulerUtils.JITTER_FRACTION;

import com.agentengine.scheduler.api.models.JobDefinition;
import com.agentengine.scheduler.api.models.TriggerDefinition;
import com.agentengine.scheduler.api.models.TriggerStatus;
import com.agentengine.scheduler.api.runner.SchedulerService;
import com.agentengine.scheduler.api.store.JobDefinitionRepository;
import com.agentengine.scheduler.api.store.TriggerDefinitionRepository;
import com.agentengine.scheduler.core.SchedulerUtils;
import com.agentengine.util.common.CronUtils;
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

    @Inject
    public SchedulerServiceImpl(
            final JobDefinitionRepository jobDefinitionRepository,
            final TriggerDefinitionRepository triggerDefinitionRepository) {
        this.jobDefinitionRepository = jobDefinitionRepository;
        this.triggerDefinitionRepository = triggerDefinitionRepository;
    }

    @Override
    public void schedule(final JobDefinition jobDefinition) {
        createTrigger(jobDefinitionRepository.save(jobDefinition));
    }

    @Override
    public JobDefinition getJob(final String jobId) {
        return jobDefinitionRepository.findById(jobId);
    }

    @Override
    public void cancelJob(final String jobId) {
        jobDefinitionRepository.deleteById(jobId);
        triggerDefinitionRepository.cancelAllJobTriggers(jobId);
    }

    /**
     * Retiring the previous triggers is housekeeping, not correctness: the scheduler already refuses
     * to dispatch a trigger whose job has moved on. Doing it here keeps the collection from
     * accumulating a queued row per reschedule — each of which would otherwise sit until its own fire
     * time before the scheduler cleared it — and makes a cancellation take effect immediately rather
     * than at the next occurrence. A failure of this write is therefore harmless.
     */
    private void createTrigger(final JobDefinition jobDefinition) {
        triggerDefinitionRepository.cancelAllJobTriggers(jobDefinition.getId());
        final TriggerDefinition triggerDefinition = new TriggerDefinition();
        triggerDefinition.setJobDefinition(jobDefinition);
        final Optional<Instant> scheduled = SchedulerUtils.nextScheduledTime(triggerDefinition);
        if (scheduled.isEmpty()) {
            return;
        }
        triggerDefinition.setStatus(TriggerStatus.WAITING);
        triggerDefinition.setScheduledFor(scheduled.get().toEpochMilli());
        triggerDefinition.setDueAt(CronUtils.applyJitter(scheduled.get(), Instant.now(), JITTER_FRACTION)
                .toEpochMilli());
        triggerDefinitionRepository.save(triggerDefinition);
    }
}
