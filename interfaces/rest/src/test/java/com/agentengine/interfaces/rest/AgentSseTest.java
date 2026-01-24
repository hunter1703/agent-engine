package com.agentengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.AgentListener;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.interfaces.rest.handlers.BuildPromptRequestHandler;
import com.agentengine.interfaces.rest.handlers.AgentRequestHandler;
import com.agentengine.interfaces.rest.handlers.InvokeAgentRequestHandler;
import com.agentengine.interfaces.rest.handlers.StreamingInvokeAgentRequestHandler;
import com.agentengine.interfaces.rest.services.AgentManager;
import com.agui.core.event.BaseEvent;
import com.agui.core.type.EventType;
import io.smallrye.mutiny.Multi;
import com.agentengine.interfaces.rest.support.HandlerInstance;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentSseTest {

  @Test
  void eventsStreamEmitsListenerEvents() {
    AgentManager service = mock(AgentManager.class);
    Agent engine = mock(Agent.class);

    when(service.getOrStartEngine(eq("agent"), eq("config.json"))).thenReturn(engine);
    doAnswer(invocation -> {
      AgentListener listener = invocation.getArgument(2);
      listener.onRunStarted("session", "run-1");
      listener.onTextMessageStart("session", "msg-1", "assistant");
      listener.onTextMessageDelta("session", "msg-1", "hello");
      listener.onTextMessageEnd("session", "msg-1");
      listener.onRunFinished("session", "run-1");
      return Message.assistant("hello");
    }).when(engine).invoke(any(), any(), any());

    AgentRestAPI resource = new AgentRestAPI(buildHandlers(service));
    AgentRequest request = new AgentRequest();
    request.setAgentName("agent");
    request.setAgentConfigPath("config.json");
    request.setSessionId("session");
    request.setMessage("hello");
    request.setType(RequestType.STREAMING_INVOKE_AGENT.name());
    Multi<BaseEvent> stream = resource.events(request);
    assertThat(stream).isNotNull();
    final var events = stream.collect().asList().await().indefinitely();

    assertThat(events).hasSize(5);
    assertThat(events.getFirst().getType()).isEqualTo(EventType.RUN_STARTED);
    assertThat(events.get(1).getType()).isEqualTo(EventType.TEXT_MESSAGE_START);
    assertThat(events.get(2).getType()).isEqualTo(EventType.TEXT_MESSAGE_CONTENT);
    assertThat(events.get(3).getType()).isEqualTo(EventType.TEXT_MESSAGE_END);
    assertThat(events.get(4).getType()).isEqualTo(EventType.RUN_FINISHED);
  }

  private static Instance<AgentRequestHandler> buildHandlers(final AgentManager service) {
    InvokeAgentRequestHandler invokeHandler = new InvokeAgentRequestHandler(service);
    BuildPromptRequestHandler buildPromptHandler = new BuildPromptRequestHandler(service);
    StreamingInvokeAgentRequestHandler streamingHandler = new StreamingInvokeAgentRequestHandler(service);
    return new HandlerInstance(List.<AgentRequestHandler>of(invokeHandler, buildPromptHandler, streamingHandler));
  }
}
