package com.agentengine.engine;

import com.agentengine.engine.api.beans.session.Plan;
import com.agentengine.engine.api.beans.session.PlanStatus;
import com.agentengine.engine.tools.Planning;
import com.google.adk.tools.ToolContext;
import com.google.adk.sessions.Session;
import com.google.adk.agents.InvocationContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentE2ETest {

  @Test
  void testPlanningFlow() {
    // Simulate runtime context
    String sessionId = "e2e-session";
    Session session = Session.builder(sessionId).appName("agent").userId("default").state(new ConcurrentHashMap<>())
        .events(new ArrayList<>()).build();

    InvocationContext invocationContext = InvocationContext.builder().session(session).build();

    ToolContext toolContext = ToolContext.builder(invocationContext).build();

    Planning planning = new Planning();

    // 1. Create a Plan
    List<Map<String, Object>> subtasks = List.of(
        Map.of("name", "Task 1", "description", "First task", "expected_outcome", "Outcome 1"),
        Map.of("name", "Task 2", "description", "Second task", "expected_outcome", "Outcome 2"));

    Map<String, Object> createResult = planning.createPlan(toolContext, null, "Master Plan", "Execute E2E Test",
        "Success", subtasks);
    assertThat(createResult).containsEntry("status", "success");
    String planId = (String) createResult.get("plan_id");
    assertThat(planId).isNotNull();

    // 2. View Plan
    Plan currentPlan = planning.viewCurrentPlan(toolContext);
    assertThat(currentPlan).isNotNull();
    assertThat(currentPlan.getId()).isEqualTo(planId);
    assertThat(currentPlan.getSubtasks()).hasSize(2);
    assertThat(currentPlan.getStatus()).isEqualTo(PlanStatus.TODO);

    // 3. Update Subtask State
    Plan subtask1 = currentPlan.getSubtasks().get(0);
    planning.updateSubtaskState(toolContext, subtask1.getId(), "IN_PROGRESS", null);

    currentPlan = planning.viewCurrentPlan(toolContext);
    assertThat(currentPlan.getSubtasks().get(0).getStatus()).isEqualTo(PlanStatus.IN_PROGRESS);

    // 4. Mark Subtask Done
    planning.updateSubtaskState(toolContext, subtask1.getId(), "DONE", "Completed successfully");

    currentPlan = planning.viewCurrentPlan(toolContext);
    assertThat(currentPlan.getSubtasks().get(0).getStatus()).isEqualTo(PlanStatus.DONE);
    assertThat(currentPlan.getSubtasks().get(0).getOutcome()).isEqualTo("Completed successfully");

    // 5. Finish Plan
    planning.finishPlan(toolContext, planId, "DONE", "All goals met");

    currentPlan = planning.viewCurrentPlan(toolContext);
    assertThat(currentPlan.getStatus()).isEqualTo(PlanStatus.DONE);
    assertThat(currentPlan.getOutcome()).isEqualTo("All goals met");
  }
}
