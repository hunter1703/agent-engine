package com.agentengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.agents.AgentRunner;
import com.agentengine.engine.repository.AgentRepository;
import com.agentengine.interfaces.rest.dto.AgentResponse;
import com.agentengine.interfaces.rest.dto.InvokeResponse;
import com.agentengine.interfaces.rest.handlers.AgentRequestHandler;
import com.agentengine.interfaces.rest.handlers.InvokeAgentRequestHandler;
import com.agentengine.interfaces.rest.handlers.StreamAguiEventsRequestHandler;
import com.agentengine.interfaces.rest.handlers.StreamResponsesRequestHandler;
import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.agents.AgentSessionRuntimeManager;
import com.agentengine.engine.agents.AgentSessionRuntime;
import com.google.adk.events.Event;
import com.google.adk.agents.RunConfig;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.smallrye.common.annotation.RunOnVirtualThread;
import com.agentengine.interfaces.rest.support.HandlerInstance;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

class AgentRestAPITest {

  @Test
  void invokeReturnsSessionAndResponse() {
    AgentSessionRuntimeManager service = mock(AgentSessionRuntimeManager.class);
    Runner runner = mock(Runner.class);
    BaseSessionService sessionService = mock(BaseSessionService.class);
    when(service.getOrStartRuntime("agent", "config.json")).thenReturn(new AgentSessionRuntime("session", runner));
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
    AgentSessionRuntimeManager service = mock(AgentSessionRuntimeManager.class);
    Runner runner = mock(Runner.class);
    BaseSessionService sessionService = mock(BaseSessionService.class);
    when(service.getOrStartRuntime("agent", "config.json")).thenReturn(new AgentSessionRuntime("session", runner));
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
  void resourceRunsOnVirtualThread() {
    assertThat(AgentRestAPI.class.isAnnotationPresent(RunOnVirtualThread.class)).isTrue();
  }

  @Test
  void createAgentStoresConfig() {
    final AgentRepository configRepository = mock(AgentRepository.class);
    final AgentConfig config = buildValidAgentConfig();
    when(configRepository.insert(config)).thenReturn(config);

    final AgentRestAPI resource = new AgentRestAPI(buildHandlers(mock(AgentSessionRuntimeManager.class)),
        configRepository);

    final AgentConfig response = resource.createAgent(config);

    assertThat(response).isInstanceOf(AgentConfig.class);
    final AgentConfig created = response;
    assertThat(created.getId()).isEqualTo("agent");
  }

  @Test
  void createAgentGeneratesIdWhenMissing() {
    final AgentRepository configRepository = mock(AgentRepository.class);
    final AgentConfig config = buildValidAgentConfig();
    config.setId(null);
    when(configRepository.insert(any(AgentConfig.class))).thenAnswer(assignId("generated-id"));

    final AgentRestAPI resource = new AgentRestAPI(buildHandlers(mock(AgentSessionRuntimeManager.class)),
        configRepository);

    final AgentConfig response = resource.createAgent(config);

    assertThat(response.getId()).isEqualTo("generated-id");
  }

  private static Instance<AgentRequestHandler<?>> buildHandlers(final AgentSessionRuntimeManager service) {
    final AgentRunner agentRunner = mock(AgentRunner.class);
    final StreamAguiEventsRequestHandler streamingHandler = new StreamAguiEventsRequestHandler(service, agentRunner);
    final InvokeAgentRequestHandler invokeHandler = new InvokeAgentRequestHandler(service, agentRunner);
    final StreamResponsesRequestHandler responsesHandler = new StreamResponsesRequestHandler(service, streamingHandler,
        agentRunner);
    return new HandlerInstance(List.of(invokeHandler, streamingHandler, responsesHandler));
  }

  private static AgentConfig buildValidAgentConfig() {
    final AgentConfig config = new AgentConfig();
    config.setId("agent");
    final AgentModelConfig modelConfig = new AgentModelConfig();
    modelConfig.setModelId("model");
    modelConfig.setSystemPrompt("system");
    config.setModel(modelConfig);
    return config;
  }

  private static Answer<AgentConfig> assignId(final String id) {
    return invocation -> {
      final AgentConfig config = invocation.getArgument(0);
      if (config.getId() == null) {
        config.setId(id);
      }
      return config;
    };
  }
}
