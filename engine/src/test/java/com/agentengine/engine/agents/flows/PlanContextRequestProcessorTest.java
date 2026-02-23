package com.agentengine.engine.agents.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.tools.planning.beans.Task;
import com.agentengine.engine.tools.planning.PlanningUtils;
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
    Task task = new Task("Step 1", "Do work");
    task.setId("task-1");
    Plan plan = new Plan("Root", "Plan goal");
    plan.setId("plan-1");
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

    PlanContextRequestProcessor processor = new PlanContextRequestProcessor();
    LlmRequest updated = processor.processRequest(context, request).blockingGet().updatedRequest();

    String instructions = String.join("\n", updated.getSystemInstructions());
    assertThat(instructions).contains("PLAN CONTEXT").contains("Root").contains("task-1");
  }
}
