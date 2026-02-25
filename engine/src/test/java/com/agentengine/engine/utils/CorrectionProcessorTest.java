package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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

    ViolationUtils.addViolation(context, Violation.builder("v1")
        .message("Message 1")
        .correctionMessage("Correct 1")
        .build());
    ViolationUtils.addViolation(context, Violation.builder("v2")
        .message("Message 2")
        .correctionMessage("Correct 2")
        .build());

    final LlmRequest request = LlmRequest.builder().build();
    final LlmRequest updated = processor.processRequest(context, request).blockingGet().updatedRequest();

    final List<Content> contents = updated.contents();
    assertThat(contents).hasSize(1);
    
    final String prompt = contents.get(0).parts().orElse(List.of()).get(0).text().orElse("");
    assertThat(prompt).contains("Correct 1").contains("Correct 2");
    
    // Verify cleared
    assertThat(ViolationUtils.getViolations(context)).isEmpty();
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

    ViolationUtils.addViolation(context, Violation.builder("planning_error")
        .message("Bad Plan")
        .correctionMessage("Please fix your plan hierarchy.")
        .build());

    final LlmRequest request = LlmRequest.builder().build();
    final LlmRequest updated = processor.processRequest(context, request).blockingGet().updatedRequest();

    final String prompt = updated.contents().get(0).parts().orElse(List.of()).get(0).text().orElse("");
    assertThat(prompt).contains("Please fix your plan hierarchy.");
  }
}
