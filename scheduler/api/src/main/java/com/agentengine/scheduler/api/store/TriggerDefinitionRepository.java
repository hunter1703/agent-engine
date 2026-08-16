package com.agentengine.scheduler.api.store;

import com.agentengine.scheduler.api.models.TriggerDefinition;
import com.agentengine.util.common.repository.Repository;
import java.util.Collection;
import java.util.List;

public interface TriggerDefinitionRepository extends Repository<TriggerDefinition> {

    /**
     * Triggers whose fire time has passed, oldest first, carrying only what the scheduler needs to
     * decide their fate: the trigger id and the embedded job's id, version and tags.
     */
    List<TriggerDefinition> findDueTriggers(int limit);

    /** Cancels triggers the scheduler has decided are out of date. */
    long cancelTriggers(Collection<String> triggerIds);

    /**
     * Moves triggers to queued and stamps them with the scheduler that took them, only where they are
     * still waiting. The status predicate makes this a compare-and-set, so two schedulers cannot both
     * take the same trigger.
     *
     * @return how many were taken — but not which, hence {@link #findQueuedBy}
     */
    long queueTriggers(Collection<String> triggerIds, String scheduledBy);

    /**
     * The queued triggers this scheduler owns, in full, ready to dispatch. Unbounded by design: only
     * triggers this scheduler just queued can be here, and those came from a scan that was itself
     * bounded.
     */
    List<TriggerDefinition> findQueuedBy(String scheduledBy);

    /** Returns a queued trigger to waiting, e.g. when no node had capacity to run it. */
    void releaseTrigger(String triggerDefinitionId);

    /** Updates the heartbeat timestamp for a running trigger to maintain its lease. */
    boolean heartbeat(String triggerId);

    /** Reclaims triggers stuck in QUEUED or RUNNING whose heartbeat has timed out. */
    long recoverTriggers(long heartbeatTimeout);

    /** Cancels every non-terminal trigger for a job, e.g. when the job itself is deleted. */
    long cancelAllJobTriggers(String jobId);
}
