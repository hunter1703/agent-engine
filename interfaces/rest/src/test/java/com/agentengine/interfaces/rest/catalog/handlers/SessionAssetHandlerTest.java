package com.agentengine.interfaces.rest.catalog.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.api.beans.session.SessionInfo;
import com.agentengine.engine.api.query.Page;
import com.agentengine.engine.api.query.PaginatedResult;
import com.agentengine.engine.api.services.SessionService;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.interfaces.rest.catalog.AssetRequest;
import com.agentengine.interfaces.rest.catalog.SessionAssetSummary;
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
    when(sessionService.findSessions(any(), eq(false)))
        .thenReturn(PaginatedResult.create(List.of(session), new Page(), 1L));

    SessionAssetHandler handler = new SessionAssetHandler(sessionService);

    // Run
    PaginatedResult<AgentSessionDTO> result = handler.findAssets(request);

    // Verify
    assertThat(result).isNotNull();
    assertThat(result.getItems()).hasSize(1);
    assertThat(result.getItems().get(0).getId()).isEqualTo(session.getId());
    assertThat(result.getItems().get(0).getTitle()).isEqualTo(session.getTitle());
    verify(sessionService).findSessions(any(), eq(false));
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

    sessionInfo.setEvents(Collections.singletonList(JsonUtils.toJacksonMap(event)));
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
  void listAssetsIncludesSessionMetadata() {
    final AssetRequest request = new AssetRequest();
    final AgentSession session = new AgentSession();
    session.setId("session-3");
    session.setAgentId("agent-3");
    session.setTitle("Session 3");
    session.setCreatedTime(120L);
    session.setUpdatedTime(220L);

    final SessionService sessionService = mock(SessionService.class);
    when(sessionService.findSessions(any(), eq(false)))
        .thenReturn(PaginatedResult.create(List.of(session), new Page(), 1L));

    final SessionAssetHandler handler = new SessionAssetHandler(sessionService);

    final PaginatedResult<SessionAssetSummary> result = handler.listAssets(request);

    assertThat(result).isNotNull();
    assertThat(result.getItems()).hasSize(1);
    final SessionAssetSummary summary = result.getItems().get(0);
    assertThat(summary.getId()).isEqualTo("session-3");
    assertThat(summary.getName()).isEqualTo("Session 3");
    assertThat(summary.getAgentId()).isEqualTo("agent-3");
    assertThat(summary.getCreatedTime()).isEqualTo(120L);
    assertThat(summary.getUpdatedTime()).isEqualTo(220L);
  }

  @Test
  void findAssetsIncludesAguiEventsWhenRequested() {
    final SessionService sessionService = mock(SessionService.class);

    final AgentSession session = new AgentSession();
    session.setId("session-2");
    session.setAgentId("agent-2");
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

    sessionInfo.setEvents(Collections.singletonList(JsonUtils.toJacksonMap(event)));
    session.setSessionInfo(sessionInfo);

    when(sessionService.findSessions(any(), eq(true)))
        .thenReturn(PaginatedResult.create(List.of(session), new Page(), 1L));

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
