package com.agentengine.core.services;

import com.agentengine.core.api.services.SessionService;
import com.agentengine.core.repository.SessionRepository;
import com.agentengine.runtime.api.services.SessionHistoryService;
import com.agentengine.util.agents.agui.AGUIEventMapper;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.update.Update;
import com.agui.core.event.BaseEvent;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Unremovable
public class SessionServiceImpl implements SessionService {

    private static final Logger LOG = LoggerFactory.getLogger(SessionServiceImpl.class);

    private final SessionRepository sessionRepository;
    private final SessionHistoryService sessionHistoryService;

    @Inject
    public SessionServiceImpl(
            final SessionRepository sessionRepository, final SessionHistoryService sessionHistoryService) {
        this.sessionRepository = sessionRepository;
        this.sessionHistoryService = sessionHistoryService;
    }

    @Override
    @WithSpan
    public AgentSession getSession(final String id) {
        return getSession(id, false);
    }

    @Override
    public AgentSession getSession(final String id, final boolean includeEvents) {
        return sanitizeSession(sessionRepository.findById(id), includeEvents);
    }

    @Override
    public Map<String, AgentSession> getSessions(final Collection<String> ids) {
        return getSessions(ids, false);
    }

    @Override
    public Map<String, AgentSession> getSessions(final Collection<String> ids, final boolean includeEvents) {
        final Map<String, AgentSession> idVsSession = sessionRepository.findByIds(ids);
        idVsSession.values().forEach(session -> sanitizeSession(session, includeEvents));
        return idVsSession;
    }

    @Override
    @WithSpan
    public PaginatedResult<AgentSession> findSessions(final Query query) {
        return sessionRepository.findByQuery(query).transform(session -> sanitizeSession(session, false));
    }

    @Override
    @WithSpan
    public boolean deleteSession(final String id) {
        return sessionRepository.deleteById(id);
    }

    @Override
    public AgentSession updateSession(final String id, final Update update) {
        return sessionRepository.update(id, update);
    }

    @Override
    public AgentSession create(final AgentSession session) {
        return sessionRepository.insert(session);
    }

    private AgentSession sanitizeSession(final AgentSession session, final boolean includeEvents) {
        if (!includeEvents || session == null) {
            return session;
        }
        LOG.info("=== sanitizeSession START - sessionId={}, agentId={} ===", session.getId(), session.getAgentId());

        final AGUIEventMapper mapper =
                new AGUIEventMapper(session.getId(), session.getAgentId(), AGUIEventMapper.Mode.REPLAY);
        final List<BaseEvent> aguiEvents = new ArrayList<>();
        final boolean isRootSession = session.getParentSessionId() == null;
        final List<SessionEvent> events = isRootSession
                ? sessionHistoryService.getAllSessionEvents(session.getId())
                : sessionHistoryService.getSessionEvents(session.getId());

        LOG.info("Retrieved {} SessionEvents from history for session {}", events.size(), session.getId());

        int eventIndex = 0;
        for (final SessionEvent event : events) {
            LOG.info(
                    "Processing SessionEvent #{} - id={}, runId={}, author={}, turnComplete={}, terminal={}",
                    eventIndex++,
                    event.getId(),
                    event.getRunId(),
                    event.getAuthor(),
                    event.getTurnComplete(),
                    event.isTerminal());

            mapper.map(event).blockingForEach(aguiEvent -> {
                LOG.info("  -> Generated AGUI event: {}", aguiEvent.getClass().getSimpleName());
                aguiEvents.add(aguiEvent);
            });
        }

        LOG.info("=== sanitizeSession END - generated {} AGUI events ===", aguiEvents.size());
        session.setAguiEvents(aguiEvents);
        return session;
    }
}
