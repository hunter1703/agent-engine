package com.agentengine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.api.handlers.BuildPromptRequestHandler;
import com.agentengine.api.handlers.InvokeAgentRequestHandler;
import com.agentengine.client.AgentRequest;
import com.agentengine.engine.AgentEngine;
import com.agentengine.engine.message.Message;
import com.agentengine.engine.message.Role;
import com.agentengine.interfaces.AgentService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentRestAPITest {

  @Test
  void invokeReturnsSessionAndResponse() {
    AgentService service = mock(AgentService.class);
    AgentEngine engine = mock(AgentEngine.class);
    when(service.getOrStartEngine("agent", "config.json")).thenReturn(engine);
    when(engine.invoke(eq("session"), any()))
        .thenReturn(new Message(Role.ASSISTANT, "ok", "t", List.of(), List.of()));

    AgentRestAPI resource =
        new AgentRestAPI(
            service, List.of(new InvokeAgentRequestHandler(), new BuildPromptRequestHandler()));
    AgentRequest request = new AgentRequest();
    request.setAgentName("agent");
    request.setAgentConfigPath("config.json");
    request.setSessionId("session");
    request.setMessage("hello");

    AgentResponse response = resource.invoke(request);

    assertThat(response).isInstanceOf(InvokeResponse.class);
    InvokeResponse invokeResponse = (InvokeResponse) response;
    assertThat(invokeResponse.sessionId()).isEqualTo("session");
    assertThat(invokeResponse.finalAnswer()).isEqualTo("ok");
    assertThat(invokeResponse.thoughts()).isEqualTo("t");
  }

  @Test
  void invokeHandlesNullEngineResponse() {
    AgentService service = mock(AgentService.class);
    AgentEngine engine = mock(AgentEngine.class);
    when(service.getOrStartEngine("agent", "config.json")).thenReturn(engine);
    when(engine.invoke(eq("session"), any())).thenReturn(null);

    AgentRestAPI resource =
        new AgentRestAPI(
            service, List.of(new InvokeAgentRequestHandler(), new BuildPromptRequestHandler()));
    AgentRequest request = new AgentRequest();
    request.setAgentName("agent");
    request.setAgentConfigPath("config.json");
    request.setSessionId("session");
    request.setMessage("hello");

    AgentResponse response = resource.invoke(request);

    assertThat(response).isInstanceOf(InvokeResponse.class);
    InvokeResponse invokeResponse = (InvokeResponse) response;
    assertThat(invokeResponse.sessionId()).isEqualTo("session");
    assertThat(invokeResponse.finalAnswer()).isNull();
    assertThat(invokeResponse.thoughts()).isNull();
  }

  @Test
  void invokeBuildPromptReturnsMessages() {
    AgentService service = mock(AgentService.class);
    AgentEngine engine = mock(AgentEngine.class);
    when(service.getOrStartEngine("agent", "config.json")).thenReturn(engine);
    when(engine.buildPrompt("session"))
        .thenReturn(List.of(Message.system("sys"), Message.user("hi")));

    AgentRestAPI resource =
        new AgentRestAPI(
            service, List.of(new InvokeAgentRequestHandler(), new BuildPromptRequestHandler()));
    AgentRequest request = new AgentRequest();
    request.setAgentName("agent");
    request.setAgentConfigPath("config.json");
    request.setSessionId("session");
    request.setType("BUILD_PROMPT");

    AgentResponse response = resource.invoke(request);

    assertThat(response).isInstanceOf(PromptResponse.class);
    PromptResponse promptResponse = (PromptResponse) response;
    assertThat(promptResponse.sessionId()).isEqualTo("session");
    assertThat(promptResponse.messages()).hasSize(2);
    assertThat(promptResponse.messages().getFirst().role()).isEqualTo("system");
  }

  @Test
  void resourceRunsOnVirtualThread() {
    assertThat(AgentRestAPI.class.isAnnotationPresent(RunOnVirtualThread.class)).isTrue();
  }
}
