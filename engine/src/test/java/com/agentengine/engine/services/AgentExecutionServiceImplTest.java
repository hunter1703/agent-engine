package com.agentengine.engine.services;

import static com.agentengine.engine.api.beans.session.AgentSession.DEFAULT_USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.agents.AgentRunner;
import com.agentengine.engine.agents.AgentSessionRuntime;
import com.agentengine.engine.agents.AgentSessionRuntimeManager;
import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.tools.multiagent.MultiAgentStateKeys;
import com.agentengine.engine.tools.multiagent.MultiAgentTelemetry;
import com.google.adk.events.Event;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.Session;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class AgentExecutionServiceImplTest {

  @Test
  void autoContinuesWithHandoffAgentWhenStateContainsHandoffRequest() {
    final AgentSessionRuntimeManager runtimeManager = mock(AgentSessionRuntimeManager.class);
    final AgentRunner agentRunner = mock(AgentRunner.class);
    final MultiAgentTelemetry telemetry = new MultiAgentTelemetry();
    final AgentExecutionServiceImpl service =
        new AgentExecutionServiceImpl(runtimeManager, agentRunner, telemetry);

    final AgentConfig sourceConfig = new AgentConfig();
    sourceConfig.setId("triage-agent");
    final Runner sourceRunner = mock(Runner.class);
    final BaseSessionService sourceSessionService = mock(BaseSessionService.class);
    when(sourceRunner.appName()).thenReturn("triage-agent");
    when(sourceRunner.sessionService()).thenReturn(sourceSessionService);
    final Session sourceSession =
        Session.builder("session-1")
            .appName("triage-agent")
            .userId(DEFAULT_USER_ID)
            .state(
                new ConcurrentHashMap<>(
                    Map.of(
                        MultiAgentStateKeys.HANDOFF_TARGET_AGENT_ID_KEY,
                        "support-agent",
                        MultiAgentStateKeys.HANDOFF_MESSAGE_KEY,
                        "Handle this as support",
                        MultiAgentStateKeys.HANDOFF_REASON_KEY,
                        "Needs specialist",
                        MultiAgentStateKeys.HANDOFF_NEXT_SESSION_ID_KEY,
                        "handoff-session-2",
                        MultiAgentStateKeys.HANDOFF_SOURCE_AGENT_ID_KEY,
                        "triage-agent",
                        MultiAgentStateKeys.HANDOFF_REQUESTED_AT_KEY,
                        123L)))
            .events(new ArrayList<>())
            .build();
    when(sourceSessionService.getSession(
            eq("triage-agent"), eq(DEFAULT_USER_ID), eq("session-1"), any()))
        .thenReturn(Maybe.just(sourceSession));
    final AgentSessionRuntime sourceRuntime =
        new AgentSessionRuntime("session-1", sourceRunner, sourceConfig);

    final AgentConfig targetConfig = new AgentConfig();
    targetConfig.setId("support-agent");
    final Runner targetRunner = mock(Runner.class);
    final BaseSessionService targetSessionService = mock(BaseSessionService.class);
    when(targetRunner.appName()).thenReturn("support-agent");
    when(targetRunner.sessionService()).thenReturn(targetSessionService);
    final Session targetSession =
        Session.builder("handoff-session-2")
            .appName("support-agent")
            .userId(DEFAULT_USER_ID)
            .state(new ConcurrentHashMap<>())
            .events(new ArrayList<>())
            .build();
    when(targetSessionService.getSession(
            eq("support-agent"), eq(DEFAULT_USER_ID), eq("handoff-session-2"), any()))
        .thenReturn(Maybe.just(targetSession));
    final AgentSessionRuntime targetRuntime =
        new AgentSessionRuntime("handoff-session-2", targetRunner, targetConfig);

    when(runtimeManager.getOrStartRuntime("triage-agent", "session-1")).thenReturn(sourceRuntime);
    when(runtimeManager.getOrStartRuntime("support-agent", "handoff-session-2"))
        .thenReturn(targetRuntime);
    when(agentRunner.run(sourceRuntime, "Initial request"))
        .thenReturn(Flowable.just(event("source-event")));
    when(agentRunner.run(targetRuntime, "Handle this as support"))
        .thenReturn(Flowable.just(event("target-event")));

    final AgentRequest request = new AgentRequest();
    request.setType(AgentRequest.RequestType.STREAM_AGUI_EVENTS.name());
    request.setAgentId("triage-agent");
    request.setSessionId("session-1");
    request.setMessage("Initial request");

    final var events = service.run(request).toList().blockingGet();
    assertThat(events).extracting(Event::id).containsExactly("source-event", "target-event");
    assertThat(sourceSession.state())
        .doesNotContainKey(MultiAgentStateKeys.HANDOFF_TARGET_AGENT_ID_KEY)
        .doesNotContainKey(MultiAgentStateKeys.HANDOFF_MESSAGE_KEY)
        .doesNotContainKey(MultiAgentStateKeys.HANDOFF_REASON_KEY)
        .doesNotContainKey(MultiAgentStateKeys.HANDOFF_NEXT_SESSION_ID_KEY)
        .doesNotContainKey(MultiAgentStateKeys.HANDOFF_SOURCE_AGENT_ID_KEY)
        .doesNotContainKey(MultiAgentStateKeys.HANDOFF_REQUESTED_AT_KEY);
    verify(runtimeManager).markRunActive("session-1");
    verify(runtimeManager).markRunInactive("session-1");
    verify(runtimeManager).markRunActive("handoff-session-2");
    verify(runtimeManager).markRunInactive("handoff-session-2");
    assertThat(telemetry.snapshot()).containsEntry("handoff_continued", 1L);
  }

  @Test
  void doesNotContinueWhenNoHandoffRequestIsPresent() {
    final AgentSessionRuntimeManager runtimeManager = mock(AgentSessionRuntimeManager.class);
    final AgentRunner agentRunner = mock(AgentRunner.class);
    final MultiAgentTelemetry telemetry = new MultiAgentTelemetry();
    final AgentExecutionServiceImpl service =
        new AgentExecutionServiceImpl(runtimeManager, agentRunner, telemetry);

    final AgentConfig config = new AgentConfig();
    config.setId("default-agent");
    final Runner runner = mock(Runner.class);
    final BaseSessionService sessionService = mock(BaseSessionService.class);
    when(runner.appName()).thenReturn("default-agent");
    when(runner.sessionService()).thenReturn(sessionService);
    final Session session =
        Session.builder("session-1")
            .appName("default-agent")
            .userId(DEFAULT_USER_ID)
            .state(new ConcurrentHashMap<>())
            .events(new ArrayList<>())
            .build();
    when(sessionService.getSession(eq("default-agent"), eq(DEFAULT_USER_ID), eq("session-1"), any()))
        .thenReturn(Maybe.just(session));

    final AgentSessionRuntime runtime = new AgentSessionRuntime("session-1", runner, config);
    when(runtimeManager.getOrStartRuntime("default-agent", "session-1")).thenReturn(runtime);
    when(agentRunner.run(runtime, "Say hello")).thenReturn(Flowable.just(event("source-event")));

    final AgentRequest request = new AgentRequest();
    request.setType(AgentRequest.RequestType.STREAM_AGUI_EVENTS.name());
    request.setAgentId("default-agent");
    request.setSessionId("session-1");
    request.setMessage("Say hello");

    final var events = service.run(request).toList().blockingGet();
    assertThat(events).extracting(Event::id).containsExactly("source-event");
    verify(runtimeManager, never()).getOrStartRuntime("support-agent", "handoff-session-2");
    assertThat(telemetry.snapshot()).containsEntry("handoff_continued", 0L);
  }

  @Test
  void blocksWhenHandoffDepthExceedsConfiguredLimit() {
    final AgentSessionRuntimeManager runtimeManager = mock(AgentSessionRuntimeManager.class);
    final AgentRunner agentRunner = mock(AgentRunner.class);
    final MultiAgentTelemetry telemetry = new MultiAgentTelemetry();
    final AgentExecutionServiceImpl service =
        new AgentExecutionServiceImpl(runtimeManager, agentRunner, telemetry);

    final AgentRequest request = new AgentRequest();
    request.setType(AgentRequest.RequestType.STREAM_AGUI_EVENTS.name());
    request.setAgentId("triage-agent");
    request.setSessionId("session-1");
    request.setMessage("Initial request");
    request.setHandoffDepth(3);
    request.setMaxHandoffDepth(2);

    Throwable thrown = null;
    try {
      service.run(request).blockingLast();
    } catch (Throwable ex) {
      thrown = ex;
    }

    assertThat(thrown).isNotNull();
    assertThat(thrown).hasMessageContaining("Handoff hop limit exceeded");
    verify(runtimeManager, never()).getOrStartRuntime(any(), any());
    assertThat(telemetry.snapshot()).containsEntry("handoff_loop_blocked", 1L);
  }

  private static Event event(final String id) {
    return Event.builder().id(id).author("model").build();
  }
}
