package com.agentengine.engine.agents.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.agents.processors.request.LoggingRequestProcessor;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.models.LlmRequest;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;

class LoggingRequestProcessorTest {

  @Test
  void returnsOriginalRequest() {
    final Event event =
        Event.builder()
            .id("event-1")
            .author("user")
            .content(Content.fromParts(Part.fromText("hello")))
            .build();
    final ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
    final Session session =
        Session.builder("session-1")
            .appName("app")
            .userId("user")
            .state(state)
            .events(new ArrayList<>(List.of(event)))
            .build();
    final InvocationContext context = InvocationContext.builder().session(session).build();
    final LlmRequest request = LlmRequest.builder().build();

    final LoggingRequestProcessor processor = new LoggingRequestProcessor();

    final LlmRequest updated =
        processor.processRequest(context, request).blockingGet().updatedRequest();

    assertThat(updated).isSameAs(request);
  }
}
