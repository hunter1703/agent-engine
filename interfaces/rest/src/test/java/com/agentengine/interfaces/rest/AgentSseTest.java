package com.agentengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agentengine.interfaces.rest.handlers.AgentRequestHandler;
import com.agentengine.interfaces.rest.handlers.BuildPromptRequestHandler;
import com.agentengine.interfaces.rest.handlers.InvokeAgentRequestHandler;
import com.agentengine.interfaces.rest.handlers.StreamingInvokeAgentRequestHandler;
import com.agentengine.interfaces.rest.responses.ResponsesApiMapper;
import com.agentengine.interfaces.rest.services.AgentRuntimeManager;
import com.agentengine.interfaces.rest.services.AgentRuntime;
import com.agui.core.event.BaseEvent;
import com.agui.core.type.EventType;
import com.google.adk.events.Event;
import com.google.adk.agents.RunConfig;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.smallrye.mutiny.Multi;
import com.agentengine.interfaces.rest.support.HandlerInstance;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentSseTest {

  @Test
  void eventsStreamEmitsListenerEvents() {
    AgentRuntimeManager service = mock(AgentRuntimeManager.class);
    Runner runner = mock(Runner.class);
    BaseSessionService sessionService = mock(BaseSessionService.class);

    when(service.getOrStartRuntime(eq("agent"), eq("config.json")))
        .thenReturn(new AgentRuntime(null, runner, sessionService, "agent"));
    when(sessionService.getSession(anyString(), anyString(), anyString(), any()))
        .thenReturn(Maybe.just(Session.builder("session").appName("agent").userId("default").build()));

    // Create an event that contains content to trigger all expected events
    Event event = Event.builder().id("event-1").invocationId("run-1").author("model")
        .content(Content.builder().role("model").parts(Part.builder().text("hello").build()).build()).partial(false) // Explicitly
                                                                                                                     // set
                                                                                                                     // partial
                                                                                                                     // to
                                                                                                                     // false
                                                                                                                     // to
                                                                                                                     // ensure
                                                                                                                     // completion
                                                                                                                     // events
                                                                                                                     // are
                                                                                                                     // triggered
        .build();

    // Mock the runner to return the event in a Flowable and ensure completion
    when(runner.runAsync(anyString(), anyString(), any(Content.class), any(RunConfig.class)))
        .thenAnswer(invocation -> Flowable.just(event).concatWith(Flowable.defer(() -> {
          // Add a small delay to ensure async processing completes
          try {
            Thread.sleep(10); // Small delay to allow async processing
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return Flowable.empty(); // Complete the flowable
        })));

    AgentRestAPI resource = new AgentRestAPI(buildHandlers(service), null);
    AgentRequest request = new AgentRequest();
    request.setAgentId("agent");
    request.setAgentConfigPath("config.json");
    request.setSessionId("session");
    request.setMessage("hello");
    request.setType(RequestType.STREAMING_INVOKE_AGENT.name());
    Multi<BaseEvent> stream = resource.events(request);
    assertThat(stream).isNotNull();

    // Wait for the events to be collected
    final var events = stream.collect().asList().await().indefinitely();

    assertThat(events).extracting(BaseEvent::getType).containsExactly(EventType.RUN_STARTED, EventType.STEP_STARTED,
        EventType.TEXT_MESSAGE_START, EventType.TEXT_MESSAGE_CONTENT, EventType.TEXT_MESSAGE_END,
        EventType.STEP_FINISHED, EventType.RUN_FINISHED);
  }

  private static Instance<AgentRequestHandler<?>> buildHandlers(final AgentRuntimeManager service) {
    final InvokeAgentRequestHandler invokeHandler = new InvokeAgentRequestHandler(service);
    final BuildPromptRequestHandler buildPromptHandler = new BuildPromptRequestHandler(service);
    final StreamingInvokeAgentRequestHandler streamingHandler = new StreamingInvokeAgentRequestHandler(service);
    return new HandlerInstance(List.of(invokeHandler, buildPromptHandler, streamingHandler));
  }
}
