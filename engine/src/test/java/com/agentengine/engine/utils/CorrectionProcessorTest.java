package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.agents.processors.request.CorrectionProcessor;
import com.agentengine.engine.api.utils.CorrectionUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.models.LlmRequest;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class CorrectionProcessorTest {

  @Test
  void injectsConsolidatedViolationsIntoRequest() {
    final CorrectionProcessor processor = new CorrectionProcessor();
    final Session session = Session.builder("s1")
        .appName("app")
        .userId("u1")
        .state(new ConcurrentHashMap<>())
        .events(new ArrayList<>())
        .build();
    final InvocationContext context = InvocationContext.builder().session(session).invocationId("inv-1").build();

    RunStateUtils.getState(context).addViolation(Violation.builder("v1")
        .message("Message 1")
        .correctionMessage("Correct 1")
        .build());
    RunStateUtils.getState(context).addViolation(Violation.builder("v2")
        .message("Message 2")
        .correctionMessage("Correct 2")
        .build());

    final LlmRequest request = LlmRequest.builder().build();
    final var result = processor.processRequest(context, request).blockingGet();
    final LlmRequest updated = result.updatedRequest();

    final List<Content> contents = updated.contents();
    assertThat(contents).hasSize(2);

    final String promptOne = contents.get(0).parts().orElse(List.of()).get(0).text().orElse("");
    final String promptTwo = contents.get(1).parts().orElse(List.of()).get(0).text().orElse("");
    assertThat(promptOne).isEqualTo("Correct 1");
    assertThat(promptTwo).isEqualTo("Correct 2");

    final List<Event> events =
        StreamSupport.stream(result.events().spliterator(), false).toList();
    assertThat(events).hasSize(2);
    assertThat(events).allMatch(CorrectionUtils::isCorrectionEvent);
    
    // Verify cleared
    assertThat(RunStateUtils.getState(context).violations()).isEmpty();
  }

  @Test
  void handlesPlanningViolations() {
    final CorrectionProcessor processor = new CorrectionProcessor();
    final Session session = Session.builder("s1")
        .appName("app")
        .userId("u1")
        .state(new ConcurrentHashMap<>())
        .events(new ArrayList<>())
        .build();
    final InvocationContext context = InvocationContext.builder().session(session).invocationId("inv-1").build();

    RunStateUtils.getState(context).addViolation(Violation.builder("planning_error")
        .message("Bad Plan")
        .correctionMessage("Please fix your plan hierarchy.")
        .build());

    final LlmRequest request = LlmRequest.builder().build();
    final var result = processor.processRequest(context, request).blockingGet();
    final LlmRequest updated = result.updatedRequest();

    final String prompt = updated.contents().get(0).parts().orElse(List.of()).get(0).text().orElse("");
    assertThat(prompt).contains("Please fix your plan hierarchy.");
    final List<Event> events =
        StreamSupport.stream(result.events().spliterator(), false).toList();
    assertThat(events).hasSize(1);
    assertThat(events.getFirst()).matches(CorrectionUtils::isCorrectionEvent);
  }
}
