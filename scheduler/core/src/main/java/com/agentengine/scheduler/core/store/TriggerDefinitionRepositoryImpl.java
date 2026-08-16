package com.agentengine.scheduler.core.store;

import com.agentengine.scheduler.api.models.TriggerDefinition;
import com.agentengine.scheduler.api.models.TriggerStatus;
import com.agentengine.scheduler.api.store.TriggerDefinitionRepository;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.query.Sort;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.mongodb.mongo.AbstractMongoRepository;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Singleton
public class TriggerDefinitionRepositoryImpl extends AbstractMongoRepository<TriggerDefinition>
    implements TriggerDefinitionRepository {

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
  public TriggerDefinitionRepositoryImpl(
      final MongoClientFactory mongoClientFactory, final ValidationService validationService) {
    super(
        mongoClientFactory,
        AssetClass.TRIGGER_DEFINITION,
        TriggerDefinition.class,
        validationService);
  }

  @Override
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
            .withExcludeFields(FIND_DUE_EXCLUDED_FIELDS)
            .withPage(new Page(0, limit));
    return findByQuery(query).getItems();
  }

  @Override
  public long cancelTriggers(final Collection<String> triggerIds) {
    if (triggerIds.isEmpty()) {
      return 0L;
    }
    return updateMany(
        Filters.in(BaseEntity.FIELD_ID, List.copyOf(triggerIds)),
        Update.of(Operation.set(TriggerDefinition.FIELD_STATUS, TriggerStatus.CANCELLED)));
  }

  @Override
  public long queueTriggers(final Collection<String> triggerIds, final String scheduledBy) {
    if (CollectionUtils.isEmpty(triggerIds)) {
      return 0L;
    }
    return updateMany(
        Filters.and(
            Filters.in(BaseEntity.FIELD_ID, List.copyOf(triggerIds)),
            Filters.eq(TriggerDefinition.FIELD_STATUS, TriggerStatus.WAITING)),
        Update.of(
            Operation.set(TriggerDefinition.FIELD_STATUS, TriggerStatus.QUEUED),
            Operation.set(TriggerDefinition.FIELD_SCHEDULED_BY, scheduledBy),
            Operation.set(TriggerDefinition.FIELD_LAST_HEARTBEAT, System.currentTimeMillis())));
  }

  @Override
  public List<TriggerDefinition> findQueuedBy(final String scheduledBy) {
    final Filter filter =
        Filters.and(
            Filters.eq(TriggerDefinition.FIELD_STATUS, TriggerStatus.QUEUED),
            Filters.eq(TriggerDefinition.FIELD_SCHEDULED_BY, scheduledBy));
    return findByQuery(new Query().withFilter(filter)).getItems();
  }

  @Override
  public void releaseTrigger(final String triggerDefinitionId) {
    updateOne(
        Filters.and(
            Filters.eq(BaseEntity.FIELD_ID, triggerDefinitionId),
            Filters.eq(TriggerDefinition.FIELD_STATUS, TriggerStatus.QUEUED)),
        Update.of(
            Operation.set(TriggerDefinition.FIELD_STATUS, TriggerStatus.WAITING),
            Operation.unset(TriggerDefinition.FIELD_SCHEDULED_BY)));
  }

  @Override
  public long cancelAllJobTriggers(final String jobId) {
    final Filter filter =
        Filters.and(
            Filters.eq(TriggerDefinition.FIELD_JOB_ID, jobId),
            Filters.nin(TriggerDefinition.FIELD_STATUS, TERMINAL_STATUSES));
    return updateMany(
        filter, Update.of(Operation.set(TriggerDefinition.FIELD_STATUS, TriggerStatus.CANCELLED)));
  }

  @Override
  public boolean heartbeat(final String triggerId) {
    final long updated =
        updateOne(
            Filters.and(
                Filters.eq(BaseEntity.FIELD_ID, triggerId),
                Filters.eq(TriggerDefinition.FIELD_STATUS, TriggerStatus.RUNNING)),
            Update.of(
                Operation.set(TriggerDefinition.FIELD_LAST_HEARTBEAT, System.currentTimeMillis())));
    return updated > 0;
  }

  @Override
  public long recoverTriggers(final long heartbeatTimeout) {
    final Filter filter =
        Filters.and(
            Filters.in(
                TriggerDefinition.FIELD_STATUS,
                List.of(TriggerStatus.QUEUED, TriggerStatus.RUNNING)),
            Filters.lt(
                TriggerDefinition.FIELD_LAST_HEARTBEAT,
                System.currentTimeMillis() - heartbeatTimeout));

    return updateMany(
        filter,
        Update.of(
            Operation.set(TriggerDefinition.FIELD_STATUS, TriggerStatus.WAITING),
            Operation.unset(TriggerDefinition.FIELD_SCHEDULED_BY)));
  }
}
