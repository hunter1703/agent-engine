package com.agentengine.agent.infra.session;

import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.agents.repository.AgentDocumentStoreClientType;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.query.Sort;
import com.agentengine.util.common.repository.AbstractRepository;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.common.validation.ValidationService;
import com.google.adk.events.Event;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@Startup
public class SessionEventsRepository extends AbstractRepository<SessionEvent> {

  /**
   * An event's created time is when it happened, not when it was stored, so an update may set it.
   */
  private static final Set<String> CONTEXTUAL_FIELDS =
      BaseEntity.CONTEXTUAL_FIELDS.stream()
          .filter(field -> !BaseEntity.FIELD_CREATED_TIME.equals(field))
          .collect(Collectors.toUnmodifiableSet());

  @Inject
  public SessionEventsRepository(
      final DocumentBackend documentBackend, final ValidationService validationService) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.perCustomer(
                AgentDocumentStoreClientType.AGENT, SessionEvent.class)),
        validationService);
  }

  /** Returns the events committed under {@code turnId}, in sequence order. */
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

  /**
   * Returns the canonical events for {@code sessionId} — or, when {@code includeChildSessions} is
   * true, for the whole session tree rooted at it (root and every child session).
   */
  public List<SessionEvent> getCommittedEvents(
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

  public PaginatedResult<SessionEvent> getCommittedEvents(
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

  public PaginatedResult<SessionEvent> getLatestCommittedEvents(
      final String sessionId, final Page page) {
    final Query query =
        new Query()
            .withFilter(committedEventsFilter(sessionId, false))
            .withSorts(
                List.of(
                    new Sort(BaseEntity.FIELD_CREATED_TIME, Sort.Order.DESC),
                    new Sort(SessionEvent.FIELD_SEQUENCE, Sort.Order.DESC)))
            .withPage(page);
    return findByQuery(query);
  }

  @Override
  protected Set<String> contextualFields() {
    return CONTEXTUAL_FIELDS;
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
