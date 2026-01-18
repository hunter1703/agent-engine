package com.agentengine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.client.AgentRequest;
import com.agentengine.client.AgentRequest.RequestType;
import com.agentengine.engine.AgentEngine;
import com.agentengine.engine.AgentListener;
import com.agentengine.engine.events.AgentEvent;
import com.agentengine.engine.message.Message;
import com.agentengine.interfaces.AgentService;
import com.agentengine.api.handlers.BuildPromptRequestHandler;
import com.agentengine.api.handlers.AgentRequestHandler;
import com.agentengine.api.handlers.InvokeAgentRequestHandler;
import com.agentengine.api.handlers.StreamingInvokeAgentRequestHandler;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.inject.Instance;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AgentSseTest {

  @Test
  void eventsStreamEmitsListenerEvents() {
    AgentService service = mock(AgentService.class);
    AgentEngine engine = mock(AgentEngine.class);
    AtomicReference<AgentListener> listenerRef = new AtomicReference<>();
    doAnswer(invocation -> {
      listenerRef.set(invocation.getArgument(1));
      return null;
    }).when(engine).registerListener(any(), any());

    when(service.getOrStartEngine(eq("agent"), eq("config.json"))).thenReturn(engine);
    when(engine.invoke(eq("session"), any())).thenAnswer(invocation -> {
      AgentListener listener = listenerRef.get();
      if (listener != null) {
        listener.onReasoningStart("session");
        listener.onFinalAnswer("session", Message.assistant("done", null));
      }
      return Message.assistant("done", null);
    });

    AgentRestAPI resource = new AgentRestAPI(buildHandlers(service));
    AgentRequest request = new AgentRequest();
    request.setAgentName("agent");
    request.setAgentConfigPath("config.json");
    request.setSessionId("session");
    request.setMessage("hello");
    request.setType(RequestType.STREAMING_INVOKE_AGENT.name());
    Multi<AgentEvent> stream = resource.events(request);
    assertThat(stream).isNotNull();

    List<AgentEvent> events = stream.collect().asList().await().atMost(Duration.ofSeconds(2));

    assertThat(events).extracting(AgentEvent::event).contains("session", "final_answer");

    verify(engine).invoke(eq("session"), any());

  }

  private static Instance<AgentRequestHandler> buildHandlers(final AgentService service) {
    InvokeAgentRequestHandler invokeHandler = new InvokeAgentRequestHandler(service);
    BuildPromptRequestHandler buildPromptHandler = new BuildPromptRequestHandler(service);
    StreamingInvokeAgentRequestHandler streamingHandler = new StreamingInvokeAgentRequestHandler(service);
    Instance<AgentRequestHandler> instance = mock(Instance.class);
    when(instance.stream()).thenReturn(Stream.of(invokeHandler, buildPromptHandler, streamingHandler));
    return instance;
  }
}
