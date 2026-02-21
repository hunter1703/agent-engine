package com.agentengine.interfaces.rest.catalog.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.api.beans.session.SessionInfo;
import com.agentengine.engine.api.services.SessionService;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.Page;
import com.agentengine.engine.api.utils.PaginatedResult;
import com.agentengine.interfaces.rest.catalog.AssetRequest;
import com.agentengine.interfaces.rest.dto.AgentSessionDTO;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SessionAssetHandlerTest {

  @Test
  void getAssetByKeyReturnsCorrectAsset() {
    // Mock SessionService
    SessionService sessionService = mock(SessionService.class);
    AgentSession session = new AgentSession();
    session.setId("test-session");
    session.setTitle("Test Session");
    when(sessionService.getSession("test-session")).thenReturn(Optional.of(session));

    SessionAssetHandler handler = new SessionAssetHandler(sessionService);
  }

  @Test
  void findAssetsReturnsPaginatedResult() {
    AssetRequest request = new AssetRequest();
    AgentSession session = new AgentSession();
    session.setId("session-1");
    session.setTitle("Session 1");

    // Mock SessionService
    SessionService sessionService = mock(SessionService.class);
    when(sessionService.findSessions(any()))
        .thenReturn(PaginatedResult.create(List.of(session), new Page()));

    SessionAssetHandler handler = new SessionAssetHandler(sessionService);

    // Run
    PaginatedResult<AgentSessionDTO> result = handler.findAssets(request);

    // Verify
    assertThat(result).isNotNull();
    assertThat(result.getItems()).hasSize(1);
    assertThat(result.getItems().get(0).getId()).isEqualTo(session.getId());
    assertThat(result.getItems().get(0).getTitle()).isEqualTo(session.getTitle());
  }

  @Test
  void getAssetsByIdsIncludesAguiEventsWhenRequested() {
    // Mock SessionService
    final SessionService sessionService = mock(SessionService.class);

    AgentSession session = new AgentSession();
    session.setId("test-session");
    session.setAgentId("test-agent"); // Required for AGUIEventMapper
    session.setTitle("Test Session");

    // Mock SessionInfo with events
    SessionInfo sessionInfo = new SessionInfo();
    sessionInfo.setId("test-session");

    // Create valid Event
    Event event =
        Event.builder()
            .id("event-1")
            .invocationId("run-1")
            .author("user")
            .content(
                Content.builder().role("user").parts(Part.builder().text("test").build()).build())
            .build();

    // Convert to map using JsonUtils
    Map<String, Object> eventMap = JsonUtils.toJacksonMap(event);
    sessionInfo.setEvents(Collections.singletonList(eventMap));
    session.setSessionInfo(sessionInfo);

    when(sessionService.getSession("test-session")).thenReturn(Optional.of(session));

    SessionAssetHandler handler = new SessionAssetHandler(sessionService);

    AssetRequest request = new AssetRequest();
    request.setKeys(List.of("test-session"));
    request.setOptions(Map.of(SessionAssetHandler.INCLUDE_EVENTS_OPTION, "true"));

    Map<String, AgentSessionDTO> result = null;
    try {
      result = handler.getAssetsByIds(request);
    } catch (Exception e) {
      e.printStackTrace();
      throw e;
    }

    assertThat(result).containsKey("test-session");
    AgentSessionDTO resultSession = result.get("test-session");
    // Verify events are mapped to the DTO events list
    assertThat(resultSession.getEvents()).isNotEmpty();
    // SessionInfo is cleared in the DTO
    assertThat(resultSession.getSessionInfo()).isNull();
  }

  @Test
  void findAssetsIncludesAguiEventsWhenRequested() {
    final SessionService sessionService = mock(SessionService.class);

    final AgentSession session = new AgentSession();
    session.setId("session-2");
    session.setAgentId("agent-2");
    session.setUserId("user-2");
    session.setTitle("Session");

    // Mock SessionInfo with events
    SessionInfo sessionInfo = new SessionInfo();
    sessionInfo.setId("session-2");

    // Create valid Event
    Event event =
        Event.builder()
            .id("event-2")
            .invocationId("run-2")
            .author("model")
            .content(
                Content.builder().role("model").parts(Part.builder().text("done").build()).build())
            .build();

    Map<String, Object> eventMap = JsonUtils.toJacksonMap(event);
    sessionInfo.setEvents(Collections.singletonList(eventMap));
    session.setSessionInfo(sessionInfo);

    when(sessionService.findSessions(any()))
        .thenReturn(PaginatedResult.create(List.of(session), new Page()));

    final SessionAssetHandler handler = new SessionAssetHandler(sessionService);
    final AssetRequest request = new AssetRequest();
    request.setOptions(Map.of("includeEvents", true));

    final PaginatedResult<AgentSessionDTO> result = handler.findAssets(request);
    final AgentSessionDTO asset = result.getItems().get(0);

    assertThat(asset).isInstanceOf(AgentSessionDTO.class);
    assertThat(asset.getEvents()).isNotEmpty();
    assertThat(asset.getSessionInfo()).isNull();
  }
}
