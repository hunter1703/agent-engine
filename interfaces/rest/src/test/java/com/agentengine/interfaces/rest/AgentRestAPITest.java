package com.agentengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.interfaces.rest.dto.AgentResponse;
import com.agentengine.interfaces.rest.dto.InvokeResponse;
import com.agentengine.interfaces.rest.dto.PromptResponse;
import com.agentengine.interfaces.rest.handlers.AgentRequestHandler;
import com.agentengine.interfaces.rest.handlers.BuildPromptRequestHandler;
import com.agentengine.interfaces.rest.handlers.InvokeAgentRequestHandler;
import com.agentengine.interfaces.rest.handlers.StreamAguiEventsRequestHandler;
import com.agentengine.interfaces.rest.handlers.StreamResponsesRequestHandler;
import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agentengine.interfaces.rest.services.AgentRuntimeManager;
import com.agentengine.interfaces.rest.services.AgentRuntime;
import com.google.adk.events.Event;
import com.google.adk.agents.RunConfig;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.ListEventsResponse;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.smallrye.common.annotation.RunOnVirtualThread;
import com.agentengine.interfaces.rest.support.HandlerInstance;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentRestAPITest {

  @Test
  void invokeReturnsSessionAndResponse() {
    AgentRuntimeManager service = mock(AgentRuntimeManager.class);
    Runner runner = mock(Runner.class);
    BaseSessionService sessionService = mock(BaseSessionService.class);
    when(service.getOrStartRuntime("agent", "config.json"))
        .thenReturn(new AgentRuntime(null, runner, sessionService, "agent"));
    when(sessionService.getSession(anyString(), anyString(), anyString(), any()))
        .thenReturn(Maybe.just(Session.builder("session").appName("agent").userId("default").build()));
    Event event = Event.builder().id("event-1").invocationId("run-1").author("model")
        .content(Content.builder().role("model").parts(Part.builder().text("response").build()).build()).build();
    when(runner.runAsync(anyString(), anyString(), any(Content.class), any(RunConfig.class)))
        .thenReturn(Flowable.just(event));

    AgentRestAPI resource = new AgentRestAPI(buildHandlers(service), null);
    AgentRequest request = new AgentRequest();
    request.setAgentId("agent");
    request.setAgentConfigPath("config.json");
    request.setSessionId("session");
    request.setMessage("hello");
    request.setType(RequestType.INVOKE_AGENT.name());

    AgentResponse response = resource.invoke(request);

    assertThat(response).isInstanceOf(InvokeResponse.class);
    InvokeResponse invokeResponse = (InvokeResponse) response;
    assertThat(invokeResponse.sessionId()).isEqualTo("session");
    assertThat(invokeResponse.finalAnswer()).isEqualTo("response");
  }

  @Test
  void invokeHandlesNullEngineResponse() {
    AgentRuntimeManager service = mock(AgentRuntimeManager.class);
    Runner runner = mock(Runner.class);
    BaseSessionService sessionService = mock(BaseSessionService.class);
    when(service.getOrStartRuntime("agent", "config.json"))
        .thenReturn(new AgentRuntime(null, runner, sessionService, "agent"));
    when(sessionService.getSession(anyString(), anyString(), anyString(), any()))
        .thenReturn(Maybe.just(Session.builder("session").appName("agent").userId("default").build()));
    when(runner.runAsync(anyString(), anyString(), any(Content.class), any(RunConfig.class)))
        .thenReturn(Flowable.empty());

    AgentRestAPI resource = new AgentRestAPI(buildHandlers(service), null);
    AgentRequest request = new AgentRequest();
    request.setAgentId("agent");
    request.setAgentConfigPath("config.json");
    request.setSessionId("session");
    request.setMessage("hello");
    request.setType(RequestType.INVOKE_AGENT.name());

    AgentResponse response = resource.invoke(request);

    assertThat(response).isInstanceOf(InvokeResponse.class);
    InvokeResponse invokeResponse = (InvokeResponse) response;
    assertThat(invokeResponse.sessionId()).isEqualTo("session");
    assertThat(invokeResponse.finalAnswer()).isNull();
  }

  @Test
  void invokeBuildPromptReturnsContents() {
    AgentRuntimeManager service = mock(AgentRuntimeManager.class);
    Runner runner = mock(Runner.class);
    BaseSessionService sessionService = mock(BaseSessionService.class);
    when(service.getOrStartRuntime("agent", "config.json"))
        .thenReturn(new AgentRuntime(null, runner, sessionService, "agent"));
    when(sessionService.getSession(anyString(), anyString(), anyString(), any()))
        .thenReturn(Maybe.just(Session.builder("session").appName("agent").userId("default").build()));
    Event systemEvent = Event.builder().id("event-1").invocationId("run-1").author("model")
        .content(Content.builder().role("model").parts(Part.builder().text("system").build()).build()).build();
    Event userEvent = Event.builder().id("event-2").invocationId("run-1").author("user")
        .content(Content.builder().role("user").parts(Part.builder().text("user").build()).build()).build();
    when(sessionService.listEvents(anyString(), anyString(), anyString()))
        .thenReturn(Single.just(ListEventsResponse.builder().events(List.of(systemEvent, userEvent)).build()));

    AgentRestAPI resource = new AgentRestAPI(buildHandlers(service), null);
    AgentRequest request = new AgentRequest();
    request.setAgentId("agent");
    request.setAgentConfigPath("config.json");
    request.setSessionId("session");
    request.setType(RequestType.BUILD_PROMPT.name());

    AgentResponse response = resource.invoke(request);

    assertThat(response).isInstanceOf(PromptResponse.class);
    PromptResponse promptResponse = (PromptResponse) response;
    assertThat(promptResponse.sessionId()).isEqualTo("session");
    assertThat(promptResponse.contents()).hasSize(2);
    assertThat(promptResponse.contents().get(0).role()).isEqualTo("assistant");
    assertThat(promptResponse.contents().get(0).content()).isEqualTo("system");
    assertThat(promptResponse.contents().get(1).role()).isEqualTo("user");
    assertThat(promptResponse.contents().get(1).content()).isEqualTo("user");
  }

  @Test
  void resourceRunsOnVirtualThread() {
    assertThat(AgentRestAPI.class.isAnnotationPresent(RunOnVirtualThread.class)).isTrue();
  }

  private static Instance<AgentRequestHandler<?>> buildHandlers(final AgentRuntimeManager service) {
    final InvokeAgentRequestHandler invokeHandler = new InvokeAgentRequestHandler(service);
    final BuildPromptRequestHandler buildPromptHandler = new BuildPromptRequestHandler(service);
    final StreamAguiEventsRequestHandler streamingHandler = new StreamAguiEventsRequestHandler(service);
    final StreamResponsesRequestHandler responsesHandler = new StreamResponsesRequestHandler(service);
    return new HandlerInstance(List.of(invokeHandler, buildPromptHandler, streamingHandler, responsesHandler));
  }
}
