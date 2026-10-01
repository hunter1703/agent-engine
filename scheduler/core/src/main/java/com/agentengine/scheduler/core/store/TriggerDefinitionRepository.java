package com.agentengine.scheduler.core.store;

import com.agentengine.scheduler.api.models.TriggerDefinition;
import com.agentengine.scheduler.api.models.TriggerStatus;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.query.Sort;
import com.agentengine.util.common.repository.AbstractRepository;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.validation.ValidationService;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@Startup
public class TriggerDefinitionRepository extends AbstractRepository<TriggerDefinition> {

  private static final List<Object> TERMINAL_STATUSES =
      List.of(TriggerStatus.SUCCEEDED, TriggerStatus.FAILED, TriggerStatus.CANCELLED);

  /**
   * Deciding a trigger's fate needs only its id and its job's identity, so both caller-sized fields
   * — the job's payload and the last run's result — are left out. A scanned row therefore stays
   * small however much data a job carries. {@link #findQueuedBy} re-reads the full documents.
   */
  private static final List<String> FIND_DUE_EXCLUDED_FIELDS =
      List.of(TriggerDefinition.FIELD_PREVIOUS_RESULT, TriggerDefinition.FIELD_JOB_PAYLOAD);

  @Inject
  public TriggerDefinitionRepository(
      final DocumentBackend documentBackend, final ValidationService validationService) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.global(
                SchedulerDocumentStoreClientType.SCHEDULER, TriggerDefinition.class)),
        validationService);
  }

  /**
   * Triggers whose fire time has passed, oldest first, carrying only what the scheduler needs to
   * decide their fate: the trigger id and the embedded job's id, version and tags.
   */
  public List<TriggerDefinition> findDueTriggers(final int limit) {
    final Filter filter =
        Filters.and(
            Filters.eq(TriggerDefinition.FIELD_STATUS, TriggerStatus.WAITING),
            Filters.lte(TriggerDefinition.FIELD_DUE_AT, Instant.now().toEpochMilli()));
    // Oldest first, so that a backlog larger than the limit drains in order rather than starving
    // whichever triggers happen to sort last.
    final Query query =
        new Query()
            .withFilter(filter)
            .withSort(new Sort(TriggerDefinition.FIELD_DUE_AT, Sort.Order.ASC))
            .withPage(new Page(0, limit));
    return findByQuery(query).getItems();
  }

  /** Triggers that are queued or running, carrying only the given fields, or all if none. */
  public List<TriggerDefinition> findInFlightTriggers(final List<String> includeFields) {
    final Query query =
        new Query()
            .withFilter(
                Filters.or(
                    Filters.eq(TriggerDefinition.FIELD_STATUS, TriggerStatus.QUEUED),
                    Filters.eq(TriggerDefinition.FIELD_STATUS, TriggerStatus.RUNNING)))
            .withIncludeFields(includeFields)
            .withPage(new Page(0, -1));
    return findByQuery(query).getItems();
  }

  /** Cancels triggers the scheduler has decided are out of date. */
  public long cancelTriggers(final Collection<String> triggerIds) {
    if (triggerIds.isEmpty()) {
      return 0L;
    }
    return updateManyIgnoringVersion(
        Filters.in(BaseEntity.FIELD_ID, List.copyOf(triggerIds)),
        Update.of(Operation.set(TriggerDefinition.FIELD_STATUS, TriggerStatus.CANCELLED)));
  }

  /**
   * Moves triggers to queued and stamps them with the worker they are for, only where they are
   * still waiting. The status predicate makes this a compare-and-set, so two schedulers cannot both
   * take the same trigger. Returns every trigger queued for that worker, not only these.
   */
  public List<TriggerDefinition> queueTriggers(
      final Collection<TriggerDefinition> triggers, final String scheduledBy) {
    if (CollectionUtils.isEmpty(triggers)) {
      return List.of();
    }
    final Set<String> triggerIds =
        triggers.stream().map(TriggerDefinition::getId).collect(Collectors.toSet());
    updateManyIgnoringVersion(
        Filters.and(
            Filters.in(BaseEntity.FIELD_ID, List.copyOf(triggerIds)),
            Filters.eq(TriggerDefinition.FIELD_STATUS, TriggerStatus.WAITING)),
        Update.of(
            Operation.set(TriggerDefinition.FIELD_STATUS, TriggerStatus.QUEUED),
            Operation.set(TriggerDefinition.FIELD_SCHEDULED_BY, scheduledBy),
            Operation.set(TriggerDefinition.FIELD_LAST_HEARTBEAT, System.currentTimeMillis())));
    return findQueuedBy(scheduledBy).stream()
        .filter(trigger -> triggerIds.contains(trigger.getId()))
        .toList();
  }

  /**
   * The queued triggers this scheduler owns, in full, ready to dispatch. Unbounded by design: only
   * triggers this scheduler just queued can be here, and those came from a scan that was itself
   * bounded.
   */
  public List<TriggerDefinition> findQueuedBy(final String scheduledBy) {
    final Filter filter =
        Filters.and(
            Filters.eq(TriggerDefinition.FIELD_STATUS, TriggerStatus.QUEUED),
            Filters.eq(TriggerDefinition.FIELD_SCHEDULED_BY, scheduledBy));
    return findByQuery(new Query().withFilter(filter)).getItems();
  }

  /** Cancels every non-terminal trigger for a job, e.g. when the job itself is deleted. */
  public long cancelAllJobTriggers(final String jobId) {
    final Filter filter =
        Filters.and(
            Filters.eq(TriggerDefinition.FIELD_JOB_ID, jobId),
            Filters.nin(TriggerDefinition.FIELD_STATUS, TERMINAL_STATUSES));
    return updateManyIgnoringVersion(
        filter, Update.of(Operation.set(TriggerDefinition.FIELD_STATUS, TriggerStatus.CANCELLED)));
  }

  /**
   * Moves a queued trigger to running, only if it is still queued for that worker. A trigger that
   * was recovered and handed to another worker in the meantime is left alone.
   */
  public boolean startTrigger(final String triggerId, final String scheduledBy) {
    final long updated =
        updateOneIgnoringVersion(
            Filters.and(
                Filters.eq(BaseEntity.FIELD_ID, triggerId),
                Filters.eq(TriggerDefinition.FIELD_STATUS, TriggerStatus.QUEUED),
                Filters.eq(TriggerDefinition.FIELD_SCHEDULED_BY, scheduledBy)),
            Update.of(
                Operation.set(TriggerDefinition.FIELD_STATUS, TriggerStatus.RUNNING),
                Operation.set(TriggerDefinition.FIELD_LAST_HEARTBEAT, System.currentTimeMillis())));
    return updated > 0;
  }

  /** Updates the heartbeat timestamp for a running trigger to maintain its lease. */
  public boolean heartbeat(final String triggerId) {
    final long updated =
        updateOneIgnoringVersion(
            Filters.and(
                Filters.eq(BaseEntity.FIELD_ID, triggerId),
                Filters.eq(TriggerDefinition.FIELD_STATUS, TriggerStatus.RUNNING)),
            Update.of(
                Operation.set(TriggerDefinition.FIELD_LAST_HEARTBEAT, System.currentTimeMillis())));
    return updated > 0;
  }

  /** Reclaims triggers stuck in QUEUED or RUNNING whose heartbeat has timed out. */
  public long recoverTriggers(final long heartbeatTimeout) {
    final Filter filter =
        Filters.and(
            Filters.in(
                TriggerDefinition.FIELD_STATUS,
                List.of(TriggerStatus.QUEUED, TriggerStatus.RUNNING)),
            Filters.lt(
                TriggerDefinition.FIELD_LAST_HEARTBEAT,
                System.currentTimeMillis() - heartbeatTimeout));

    return updateManyIgnoringVersion(
        filter,
        Update.of(
            Operation.set(TriggerDefinition.FIELD_STATUS, TriggerStatus.WAITING),
            Operation.unset(TriggerDefinition.FIELD_SCHEDULED_BY)));
  }
}
