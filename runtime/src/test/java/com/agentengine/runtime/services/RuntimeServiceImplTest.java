package com.agentengine.runtime.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.core.api.services.SessionService;
import com.agentengine.runtime.api.model.UserMessage;
import com.agentengine.runtime.api.services.SessionHistoryService;
import com.agentengine.runtime.session.SessionActorFactory;
import com.agentengine.runtime.session.SessionEventChannel;
import com.agentengine.runtime.session.StartSessionResult;
import com.agentengine.runtime.session.commands.SessionCommand;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.agents.beans.session.SessionStatus;
import com.agentengine.util.common.events.EventSubscription;
import com.agentengine.util.common.events.SequencedEvent;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.pekko.Done;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.junit.jupiter.api.Test;

class RuntimeServiceImplTest {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void shouldStreamLiveEventsWhenStartingNewTurnOnCompletedSession() {
        final SessionActorFactory sessionActorFactory = mock(SessionActorFactory.class);
        final SessionEventChannel eventChannel = mock(SessionEventChannel.class);
        final SessionService sessionService = mock(SessionService.class);
        final SessionHistoryService sessionHistoryService = mock(SessionHistoryService.class);
        final RuntimeServiceImpl runtimeService =
                new RuntimeServiceImpl(sessionActorFactory, eventChannel, sessionService, sessionHistoryService);

        final String agentId = "story_agent";
        final String sessionId = "session-1";

        final AgentSession session = new AgentSession(sessionId, agentId, Map.of());
        session.setRootSessionId(sessionId);
        session.setStatus(SessionStatus.COMPLETED);
        when(sessionService.getSession(sessionId)).thenReturn(session);

        final EntityRef<SessionCommand> ref = mock(EntityRef.class);
        when(sessionActorFactory.entityRef(sessionId)).thenReturn(ref);
        when(((EntityRef) ref).ask(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(Done.done()))
                .thenReturn(CompletableFuture.completedFuture(new StartSessionResult.Accepted()));

        final PublishProcessor<SequencedEvent<SessionEvent>> liveEvents = PublishProcessor.create();
        when(eventChannel.subscribe(sessionId))
                .thenReturn(CompletableFuture.completedFuture(new EventSubscription<>("sub-1", liveEvents)));

        final SessionEvent event = new SessionEvent();
        event.setId("event-1");
        event.setSessionId(sessionId);
        event.setRootSessionId(sessionId);

        final var subscriber = Flowable.fromPublisher(
                        runtimeService.startSession(agentId, sessionId, UserMessage.ofText("hello")))
                .test();

        liveEvents.onNext(new SequencedEvent<>(1L, event));

        assertThat(subscriber.values()).containsExactly(event);
    }
}
