package com.agentengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.api.services.AgentExecutionService;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.interfaces.rest.dto.AgentResponse;
import com.agentengine.interfaces.rest.dto.InvokeResponse;
import com.agentengine.interfaces.rest.handlers.AgentRequestHandler;
import com.agentengine.interfaces.rest.handlers.InvokeAgentRequestHandler;
import com.agentengine.interfaces.rest.handlers.StreamAguiEventsRequestHandler;
import com.agentengine.interfaces.rest.handlers.StreamResponsesRequestHandler;
import com.agentengine.interfaces.rest.support.HandlerInstance;
import com.google.adk.events.Event;
import com.google.adk.runner.Runner;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

class AgentRestAPITest {

  @Test
  void invokeReturnsSessionAndResponse() {
    AgentExecutionService executionService = mock(AgentExecutionService.class);
    Runner runner = mock(Runner.class);

    // Create an event that mimics the AgentRunner output
    Event event =
        Event.builder()
            .id("event-1")
            .invocationId("run-1")
            .author("model")
            .content(
                Content.builder()
                    .role("model")
                    .parts(Part.builder().text("response").build())
                    .build())
            .build();

    when(executionService.run(any(AgentRequest.class))).thenReturn(Flowable.just(event));

    AgentRestAPI resource = new AgentRestAPI(buildHandlers(executionService), null);
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
    AgentExecutionService executionService = mock(AgentExecutionService.class);
    when(executionService.run(any(AgentRequest.class))).thenReturn(Flowable.empty());

    AgentRestAPI resource = new AgentRestAPI(buildHandlers(executionService), null);
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
  void invokeRunsOnVirtualThread() throws NoSuchMethodException {
    assertThat(
            AgentRestAPI.class
                .getMethod("invoke", AgentRequest.class)
                .isAnnotationPresent(RunOnVirtualThread.class))
        .isTrue();
  }

  @Test
  void createAgentStoresConfig() {
    final AgentService agentService = mock(AgentService.class);
    final AgentConfig config = buildValidAgentConfig();
    when(agentService.createAgent(config)).thenReturn(config);

    final AgentRestAPI resource =
        new AgentRestAPI(buildHandlers(mock(AgentExecutionService.class)), agentService);

    final AgentConfig response = resource.createAgent(config);

    assertThat(response).isInstanceOf(AgentConfig.class);
    final AgentConfig created = response;
    assertThat(created.getId()).isEqualTo("agent");
    verify(agentService).createAgent(config);
  }

  @Test
  void createAgentGeneratesIdWhenMissing() {
    final AgentService agentService = mock(AgentService.class);
    final AgentConfig config = buildValidAgentConfig();
    config.setId(null);

    // Simulate ID generation
    doAnswer(assignId("generated-id")).when(agentService).createAgent(any(AgentConfig.class));

    final AgentRestAPI resource =
        new AgentRestAPI(buildHandlers(mock(AgentExecutionService.class)), agentService);

    final AgentConfig response = resource.createAgent(config);

    assertThat(response.getId()).isEqualTo("generated-id");
  }

  private static Instance<AgentRequestHandler<?>> buildHandlers(
      final AgentExecutionService executionService) {
    final StreamAguiEventsRequestHandler streamingHandler =
        new StreamAguiEventsRequestHandler(executionService);
    final InvokeAgentRequestHandler invokeHandler = new InvokeAgentRequestHandler(executionService);
    final StreamResponsesRequestHandler responsesHandler =
        new StreamResponsesRequestHandler(executionService, streamingHandler);
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
