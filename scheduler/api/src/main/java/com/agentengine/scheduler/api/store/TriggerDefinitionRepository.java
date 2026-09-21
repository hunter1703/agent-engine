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

  /** Triggers that are queued or running, carrying only the given fields, or all if none. */
  List<TriggerDefinition> findInFlightTriggers(List<String> includeFields);

  /** Cancels triggers the scheduler has decided are out of date. */
  long cancelTriggers(Collection<String> triggerIds);

  /**
   * Moves triggers to queued and stamps them with the worker they are for, only where they are
   * still waiting. The status predicate makes this a compare-and-set, so two schedulers cannot both
   * take the same trigger. Returns every trigger queued for that worker, not only these.
   */
  List<TriggerDefinition> queueTriggers(Collection<TriggerDefinition> triggers, String scheduledBy);

  /**
   * The queued triggers this scheduler owns, in full, ready to dispatch. Unbounded by design: only
   * triggers this scheduler just queued can be here, and those came from a scan that was itself
   * bounded.
   */
  List<TriggerDefinition> findQueuedBy(String scheduledBy);

  /**
   * Moves a queued trigger to running, only if it is still queued for that worker. A trigger that
   * was recovered and handed to another worker in the meantime is left alone.
   */
  boolean startTrigger(String triggerId, String scheduledBy);

  /** Updates the heartbeat timestamp for a running trigger to maintain its lease. */
  boolean heartbeat(String triggerId);

  /** Reclaims triggers stuck in QUEUED or RUNNING whose heartbeat has timed out. */
  long recoverTriggers(long heartbeatTimeout);

  /** Cancels every non-terminal trigger for a job, e.g. when the job itself is deleted. */
  long cancelAllJobTriggers(String jobId);
}
