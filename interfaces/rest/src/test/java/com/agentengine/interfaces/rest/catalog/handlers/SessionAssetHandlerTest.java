package com.agentengine.interfaces.rest.catalog.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.beans.session.AgentSession;
import com.agentengine.engine.builders.state.SessionServiceProvider;
import com.agentengine.engine.repository.AgentRepository;
import com.agentengine.engine.repository.AgentSessionRepository;
import com.agentengine.engine.utils.PaginatedResult;
import com.agentengine.interfaces.rest.catalog.AssetRequest;
import com.agentengine.interfaces.rest.catalog.SessionAsset;
import com.agui.core.event.BaseEvent;
import com.agui.core.event.RunFinishedEvent;
import com.agui.core.event.RunStartedEvent;
import com.google.adk.events.Event;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.ListEventsResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SessionAssetHandlerTest {

  @Test
  void getAssetsByIdsIncludesAguiEventsWhenRequested() {
    final AgentSessionRepository sessionRepository = mock(AgentSessionRepository.class);
    final AgentRepository agentRepository = mock(AgentRepository.class);
    final SessionServiceProvider sessionServiceProvider = mock(SessionServiceProvider.class);
    final BaseSessionService sessionService = mock(BaseSessionService.class);

    final AgentSession session = new AgentSession();
    session.setId("session-1");
    session.setAgentId("agent-1");
    session.setUserId("user-1");
    session.setTitle("Session");

    final AgentConfig agentConfig = new AgentConfig();
    agentConfig.setId("agent-1");

    final Event responseEvent = Event.builder().id("event-1").invocationId("run-1").author("model")
        .content(Content.builder().role("model").parts(Part.builder().text("done").build()).build()).build();
    final ListEventsResponse eventsResponse = ListEventsResponse.builder().events(List.of(responseEvent)).build();

    when(sessionRepository.findById("session-1")).thenReturn(Optional.of(session));
    when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agentConfig));
    when(sessionServiceProvider.get(agentConfig.getSessionStore())).thenReturn(sessionService);
    when(sessionService.listEvents(AgentSession.DEFAULT_APP, AgentSession.DEFAULT_USER_ID, "session-1"))
        .thenReturn(Single.just(eventsResponse));

    final SessionAssetHandler handler = new SessionAssetHandler(sessionRepository, agentRepository,
        sessionServiceProvider);
    final AssetRequest request = new AssetRequest();
    request.setKeys(List.of("session-1"));
    request.setOptions(Map.of("includeEvents", true));

    final Map<String, AgentSession> results = handler.getAssetsByIds(request);
    final AgentSession asset = results.get("session-1");

    assertThat(asset).isInstanceOf(SessionAsset.class);
    final List<BaseEvent> events = ((SessionAsset) asset).getEvents();
    assertThat(events).isNotEmpty();
    assertThat(events).anyMatch(event -> event instanceof RunStartedEvent);
    final RunFinishedEvent finishedEvent = events.stream().filter(RunFinishedEvent.class::isInstance)
        .map(RunFinishedEvent.class::cast).findFirst().orElseThrow();
    assertThat(finishedEvent.getResult()).isEqualTo("done");
  }

  @Test
  void findAssetsIncludesAguiEventsWhenRequested() {
    final AgentSessionRepository sessionRepository = mock(AgentSessionRepository.class);
    final AgentRepository agentRepository = mock(AgentRepository.class);
    final SessionServiceProvider sessionServiceProvider = mock(SessionServiceProvider.class);
    final BaseSessionService sessionService = mock(BaseSessionService.class);

    final AgentSession session = new AgentSession();
    session.setId("session-2");
    session.setAgentId("agent-2");
    session.setUserId("user-2");
    session.setTitle("Session");

    final AgentConfig agentConfig = new AgentConfig();
    agentConfig.setId("agent-2");

    final Event responseEvent = Event.builder().id("event-2").invocationId("run-2").author("model")
        .content(Content.builder().role("model").parts(Part.builder().text("done").build()).build()).build();
    final ListEventsResponse eventsResponse = ListEventsResponse.builder().events(List.of(responseEvent)).build();

    final PaginatedResult<AgentSession> paginatedResult = new PaginatedResult<>();
    paginatedResult.setItems(List.of(session));

    when(sessionRepository.findByQuery(any())).thenReturn(paginatedResult);
    when(agentRepository.findById("agent-2")).thenReturn(Optional.of(agentConfig));
    when(sessionServiceProvider.get(agentConfig.getSessionStore())).thenReturn(sessionService);
    when(sessionService.listEvents(AgentSession.DEFAULT_APP, AgentSession.DEFAULT_USER_ID, "session-2"))
        .thenReturn(Single.just(eventsResponse));

    final SessionAssetHandler handler = new SessionAssetHandler(sessionRepository, agentRepository,
        sessionServiceProvider);
    final AssetRequest request = new AssetRequest();
    request.setOptions(Map.of("includeEvents", true));

    final PaginatedResult<AgentSession> result = handler.findAssets(request);
    final AgentSession asset = result.getItems().getFirst();

    assertThat(asset).isInstanceOf(SessionAsset.class);
    assertThat(((SessionAsset) asset).getEvents()).isNotEmpty();
  }
}
