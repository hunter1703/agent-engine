package com.agentengine.engine.agents.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.tools.planning.PlanningUtils;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.tools.planning.beans.Task;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class PlanningRequestProcessorTest {

  @Test
  void appendsPlanSummaryAndTaskFocus() {
    Task task = new Task("Step 1", "Do work");
    task.setTaskId("task-1");
    Plan plan = new Plan("Root", "Plan goal");
    plan.setPlanId("plan-1");
    plan.setTasks(List.of(task));

    Session session =
        Session.builder("session-1")
            .appName("agent")
            .userId("default")
            .state(new ConcurrentHashMap<>(Map.of(PlanningUtils.PLAN_STATE_KEY, plan)))
            .events(new ArrayList<>())
            .build();

    InvocationContext context = InvocationContext.builder().session(session).build();
    LlmRequest request = LlmRequest.builder().build();

    PlanningRequestProcessor processor = new PlanningRequestProcessor();
    LlmRequest updated = processor.processRequest(context, request).blockingGet().updatedRequest();

    // Verify Instructions (from PlanContext logic)
    String instructions = String.join("\n", updated.getSystemInstructions());
    assertThat(instructions).contains("PLAN CONTEXT").contains("Root").contains("task-1");

    // Verify Content Anchor (from PlanTask logic)
    assertThat(updated.contents()).isNotEmpty();
    Content lastContent = updated.contents().get(updated.contents().size() - 1);
    String contentText = lastContent.parts().orElse(List.of()).stream()
        .map(p -> p.text().orElse(""))
        .collect(java.util.stream.Collectors.joining());
        
    assertThat(contentText).contains("### STRUCTURAL ANCHOR").contains("task-1").contains("Continue until all tasks are done");
  }

  @Test
  void doesNothingIfNoPlan() {
    Session session =
        Session.builder("session-1")
            .appName("agent")
            .userId("default")
            .state(new ConcurrentHashMap<>())
            .events(new ArrayList<>())
            .build();

    InvocationContext context = InvocationContext.builder().session(session).build();
    LlmRequest request = LlmRequest.builder().build();

    PlanningRequestProcessor processor = new PlanningRequestProcessor();
    LlmRequest updated = processor.processRequest(context, request).blockingGet().updatedRequest();

    assertThat(updated.getSystemInstructions()).isEmpty();
    assertThat(updated.contents()).isEmpty();
  }
}
