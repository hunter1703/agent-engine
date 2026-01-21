package com.agentengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.interfaces.rest.dto.AgentResponse;
import com.agentengine.interfaces.rest.dto.InvokeResponse;
import com.agentengine.interfaces.rest.dto.PromptResponse;
import com.agentengine.interfaces.rest.handlers.AgentRequestHandler;
import com.agentengine.interfaces.rest.handlers.BuildPromptRequestHandler;
import com.agentengine.interfaces.rest.handlers.InvokeAgentRequestHandler;
import com.agentengine.interfaces.rest.handlers.StreamingInvokeAgentRequestHandler;
import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agentengine.interfaces.rest.services.AGUIAgent;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.Role;
import com.agentengine.interfaces.rest.services.AgentManager;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AgentRestAPITest {

  @Test
  void invokeReturnsSessionAndResponse() {
    AgentManager service = mock(AgentManager.class);
    AGUIAgent engine = mock(AGUIAgent.class);
    when(service.getOrStartEngine("agent", "config.json")).thenReturn(engine);
    when(engine.invoke(anyString(), any(), any())).thenReturn(Message.assistant("response"));

    AgentRestAPI resource = new AgentRestAPI(buildHandlers(service));
    AgentRequest request = new AgentRequest();
    request.setAgentName("agent");
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
    AgentManager service = mock(AgentManager.class);
    AGUIAgent engine = mock(AGUIAgent.class);
    when(service.getOrStartEngine("agent", "config.json")).thenReturn(engine);
    when(engine.invoke(anyString(), any(), any())).thenReturn(null);

    AgentRestAPI resource = new AgentRestAPI(buildHandlers(service));
    AgentRequest request = new AgentRequest();
    request.setAgentName("agent");
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
  void invokeBuildPromptReturnsMessages() {
    AgentManager service = mock(AgentManager.class);
    AGUIAgent engine = mock(AGUIAgent.class);
    when(service.getOrStartEngine("agent", "config.json")).thenReturn(engine);
    when(engine.buildPrompt(anyString())).thenReturn(List.of(Message.system("system"), Message.user("user")));

    AgentRestAPI resource = new AgentRestAPI(buildHandlers(service));
    AgentRequest request = new AgentRequest();
    request.setAgentName("agent");
    request.setAgentConfigPath("config.json");
    request.setSessionId("session");
    request.setType(RequestType.BUILD_PROMPT.name());

    AgentResponse response = resource.invoke(request);

    assertThat(response).isInstanceOf(PromptResponse.class);
    PromptResponse promptResponse = (PromptResponse) response;
    assertThat(promptResponse.sessionId()).isEqualTo("session");
    assertThat(promptResponse.messages()).hasSize(2);
    assertThat(promptResponse.messages().get(0).role()).isEqualTo("system");
    assertThat(promptResponse.messages().get(0).content()).isEqualTo("system");
    assertThat(promptResponse.messages().get(1).role()).isEqualTo("user");
    assertThat(promptResponse.messages().get(1).content()).isEqualTo("user");
  }

  @Test
  void resourceRunsOnVirtualThread() {
    assertThat(AgentRestAPI.class.isAnnotationPresent(RunOnVirtualThread.class)).isTrue();
  }

  private static Instance<AgentRequestHandler> buildHandlers(final AgentManager service) {
    InvokeAgentRequestHandler invokeHandler = new InvokeAgentRequestHandler(service);
    BuildPromptRequestHandler buildPromptHandler = new BuildPromptRequestHandler(service);
    StreamingInvokeAgentRequestHandler streamingHandler = new StreamingInvokeAgentRequestHandler(service);
    Instance<AgentRequestHandler> instance = mock(Instance.class);
    when(instance.stream()).thenReturn(Stream.of(invokeHandler, buildPromptHandler, streamingHandler));
    return instance;
  }
}
