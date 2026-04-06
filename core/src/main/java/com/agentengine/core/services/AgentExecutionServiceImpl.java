package com.agentengine.core.services;

import com.agentengine.core.api.services.AgentExecutionService;
import com.agentengine.core.api.services.SessionService;
import com.agentengine.runtime.api.services.RuntimeService;
import com.agentengine.util.agents.agui.AGUIEventMapper;
import com.agentengine.util.agents.beans.Confirmation;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agui.core.event.BaseEvent;
import io.quarkus.arc.Unremovable;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Unremovable
public class AgentExecutionServiceImpl implements AgentExecutionService {

    private static final Logger LOG = LoggerFactory.getLogger(AgentExecutionServiceImpl.class);

    private final SessionService sessionService;
    private final RuntimeService runtimeService;

    @Inject
    public AgentExecutionServiceImpl(final SessionService sessionService, final RuntimeService runtimeService) {
        this.sessionService = sessionService;
        this.runtimeService = runtimeService;
    }

    @Override
    public Publisher<BaseEvent> run(final String agentId, final String sessionId, final String text) {
        final StartEventMapper mapper = new StartEventMapper(agentId);
        return Flowable.fromPublisher(runtimeService.startSession(agentId, sessionId, text))
                .concatMap(mapper::map);
    }

    @Override
    public Publisher<BaseEvent> confirmSession(
            final String sessionId, final String confirmationId, final Boolean confirmed, final String answer) {
        final AgentSession session = sessionService.getSession(sessionId);
        if (session == null) {
            throw new AssetNotFoundException(AssetClass.AGENT_SESSION, sessionId);
        }

        final String rootSessionId = session.getRootSessionId();
        final String rootAgentId = session.getRootAgentId();
        final Confirmation confirmation = new Confirmation(confirmationId, confirmed, answer);

        final AGUIEventMapper mapper = new AGUIEventMapper(rootSessionId, rootAgentId);
        return Flowable.fromPublisher(runtimeService.confirmSession(rootSessionId, confirmation))
                .concatMap(mapper::map);
    }

    private static final class StartEventMapper {
        private final String agentId;
        private AGUIEventMapper mapper;

        private StartEventMapper(final String agentId) {
            this.agentId = agentId;
        }

        private Flowable<BaseEvent> map(final SessionEvent event) {
            if (mapper == null) {
                final String sessionId = event.getRootSessionId();
                if (StringUtils.isBlank(sessionId)) {
                    throw new IllegalStateException("Runtime emitted start event without rootSessionId");
                }
                mapper = new AGUIEventMapper(sessionId, agentId);
            }
            return mapper.map(event);
        }
    }
}
