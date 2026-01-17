package com.agentengine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.AgentEngine;
import com.agentengine.engine.message.Message;
import com.agentengine.engine.message.Role;
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

    AgentRestAPI resource = new AgentRestAPI(service);
    InvokeRequest request = new InvokeRequest();
    request.setAgentName("agent");
    request.setAgentConfigPath("config.json");
    request.setSessionId("session");
    request.setMessage("hello");

    InvokeResponse response = resource.invoke(request);

    assertThat(response.sessionId()).isEqualTo("session");
    assertThat(response.finalAnswer()).isEqualTo("ok");
    assertThat(response.thoughts()).isEqualTo("t");
  }

  @Test
  void invokeHandlesNullEngineResponse() {
    AgentService service = mock(AgentService.class);
    AgentEngine engine = mock(AgentEngine.class);
    when(service.getOrStartEngine("agent", "config.json")).thenReturn(engine);
    when(engine.invoke(eq("session"), any())).thenReturn(null);

    AgentRestAPI resource = new AgentRestAPI(service);
    InvokeRequest request = new InvokeRequest();
    request.setAgentName("agent");
    request.setAgentConfigPath("config.json");
    request.setSessionId("session");
    request.setMessage("hello");

    InvokeResponse response = resource.invoke(request);

    assertThat(response.sessionId()).isEqualTo("session");
    assertThat(response.finalAnswer()).isNull();
    assertThat(response.thoughts()).isNull();
  }

  @Test
  void buildPromptReturnsMessages() {
    AgentService service = mock(AgentService.class);
    AgentEngine engine = mock(AgentEngine.class);
    when(service.getOrStartEngine("agent", "config.json")).thenReturn(engine);
    when(engine.buildPrompt("session"))
        .thenReturn(List.of(Message.system("sys"), Message.user("hi")));

    AgentRestAPI resource = new AgentRestAPI(service);
    AgentRequest request = new AgentRequest();
    request.setAgentName("agent");
    request.setAgentConfigPath("config.json");
    request.setSessionId("session");

    PromptResponse response = resource.buildPrompt(request);

    assertThat(response.sessionId()).isEqualTo("session");
    assertThat(response.messages()).hasSize(2);
    assertThat(response.messages().getFirst().role()).isEqualTo("system");
  }
}
