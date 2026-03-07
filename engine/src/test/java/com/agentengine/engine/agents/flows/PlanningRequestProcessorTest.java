package com.agentengine.engine.agents.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.agents.processors.request.PlanningRequestProcessor;
import com.agentengine.engine.utils.RunStateUtils;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.tools.planning.beans.Task;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import java.util.ArrayList;
import java.util.List;
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
            .state(new ConcurrentHashMap<>())
            .events(new ArrayList<>())
            .build();

    InvocationContext context = InvocationContext.builder().session(session).build();
    RunStateUtils.getState(context).updatePlan(plan);
    LlmRequest request = LlmRequest.builder().build();

    PlanningRequestProcessor processor = new PlanningRequestProcessor();
    LlmRequest updated = processor.processRequest(context, request).blockingGet().updatedRequest();

    final String allContentText =
        updated.contents().stream()
            .flatMap(content -> content.parts().orElse(List.of()).stream())
            .map(part -> part.text().orElse(""))
            .collect(java.util.stream.Collectors.joining("\n"));

    // Verify appended plan context summary.
    assertThat(allContentText).contains("PLAN CONTEXT").contains("Root").contains("task-1");

    // Verify structural task anchor.
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
