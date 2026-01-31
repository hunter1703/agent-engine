package com.agentengine.engine.agents.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.session.Plan;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.sessions.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class PlanContextRequestProcessorTest {

  @Test
  void appendsPlanSummaryToInstructions() {
    Plan subtask = new Plan("Step 1", "Do work", "Outcome");
    subtask.setId("task-1");
    Plan plan = new Plan("Root", "Plan desc", "Finish", List.of(subtask));
    plan.setId("plan-1");

    Session session = Session.builder("session-1")
        .appName("agent")
        .userId("default")
        .state(new ConcurrentHashMap<>(Map.of("currentPlan", plan)))
        .events(new ArrayList<>())
        .build();

    InvocationContext context = InvocationContext.builder().session(session).build();
    LlmRequest request = LlmRequest.builder().build();

    PlanContextRequestProcessor processor = new PlanContextRequestProcessor();
    LlmRequest updated = processor.processRequest(context, request).blockingGet().updatedRequest();

    String instructions = String.join("\n", updated.getSystemInstructions());
    assertThat(instructions).contains("PLAN CONTEXT").contains("plan-1").contains("task-1");
  }
}
