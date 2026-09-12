package com.agentengine.util.agents.repository;

import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.query.Sort;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.mongodb.mongo.AbstractMongoRepository;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import com.google.adk.events.Event;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Singleton
public class SessionEventsRepositoryImpl extends AbstractMongoRepository<SessionEvent>
    implements SessionEventsRepository {

  @Inject
  public SessionEventsRepositoryImpl(
      final MongoClientFactory mongoClientFactory, final ValidationService validationService) {
    super(mongoClientFactory, AssetClass.SESSION_EVENT, SessionEvent.class, validationService);
  }

  @Override
  public List<Event> findTurnEvents(final String sessionId, final String turnId) {
    if (turnId == null) {
      return List.of();
    }
    final Query query =
        new Query()
            .withFilter(
                Filters.and(
                    Filters.eq(SessionEvent.FIELD_SESSION_ID, sessionId),
                    Filters.eq(SessionEvent.FIELD_TURN_ID, turnId),
                    Filters.or(
                        Filters.notExists(SessionEvent.FIELD_ROLLBACK_ID),
                        Filters.eq(SessionEvent.FIELD_ROLLBACK_ID, null))))
            .withSort(new Sort(SessionEvent.FIELD_SEQUENCE, Sort.Order.ASC));
    return findByQuery(query).getItems().stream().map(SessionEvent::getRawEvent).toList();
  }

  @Override
  public List<SessionEvent> getCommittedSessionEvents(
      final String sessionId, final boolean includeChildSessions) {
    final Query query =
        new Query().withFilter(committedEventsFilter(sessionId, includeChildSessions));
    final List<SessionEvent> events = new ArrayList<>(findByQuery(query).getItems());

    events.sort(
        Comparator.comparingLong(SessionEvent::getTimestamp)
            .thenComparing(
                SessionEvent::getSessionId,
                (one, two) -> {
                  if (Objects.equals(sessionId, one) && !Objects.equals(sessionId, two)) {
                    return -1;
                  } else if (Objects.equals(sessionId, two) && !Objects.equals(sessionId, one)) {
                    return 1;
                  } else {
                    return one.compareTo(two);
                  }
                })
            .thenComparingLong(SessionEvent::getSequence));
    return events;
  }

  @Override
  public PaginatedResult<SessionEvent> getCommittedSessionEvents(
      final String sessionId, final boolean includeChildSessions, final Page page) {
    final Query query =
        new Query()
            .withFilter(committedEventsFilter(sessionId, includeChildSessions))
            .withSorts(
                List.of(
                    new Sort(BaseEntity.FIELD_CREATED_TIME, Sort.Order.ASC),
                    new Sort(SessionEvent.FIELD_SEQUENCE, Sort.Order.ASC)))
            .withPage(page);
    return findByQuery(query);
  }

  private static Filter committedEventsFilter(
      final String sessionId, final boolean includeChildSessions) {
    final String scopeField =
        includeChildSessions ? SessionEvent.FIELD_ROOT_SESSION_ID : SessionEvent.FIELD_SESSION_ID;
    return Filters.and(
        Filters.eq(scopeField, sessionId),
        Filters.or(
            Filters.notExists(SessionEvent.FIELD_ROLLBACK_ID),
            Filters.eq(SessionEvent.FIELD_ROLLBACK_ID, null)));
  }
}
