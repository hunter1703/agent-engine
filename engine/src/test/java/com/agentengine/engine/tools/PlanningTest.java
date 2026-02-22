package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.session.Plan;
import com.agentengine.engine.api.beans.session.PlanStatus;
import com.agentengine.engine.tools.planning.CreatePlanTool;
import com.agentengine.engine.tools.planning.PlanningUtils;
import com.agentengine.engine.tools.planning.UpdatePlanInfoTool;
import com.agentengine.engine.tools.planning.UpdateTaskTool;
import com.google.adk.agents.InvocationContext;
import com.google.adk.sessions.Session;
import com.google.adk.tools.ToolContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class PlanningTest {

  @Test
  void updatePlanInfoTargetsPlanById() {
    String sessionId = "session-1";
    Session session = buildSession(sessionId);
    ToolContext toolContext = buildToolContext(session);

    CreatePlanTool createPlanTool = new CreatePlanTool();
    UpdatePlanInfoTool updatePlanInfoTool = new UpdatePlanInfoTool();
    Map<String, Object> result =
        createPlanTool.createPlan(
            toolContext,
            null,
            "Root",
            "Desc",
            "Outcome",
            List.of(
                Map.of("name", "Task", "description", "Task desc", "expected_outcome", "Done")));
    String planId = (String) result.get("plan_id");

    updatePlanInfoTool.updatePlanInfo(toolContext, planId, "Updated", null, null);

    Plan plan = loadPlan(session);
    assertThat(plan.getId()).isEqualTo(planId);
    assertThat(plan.getName()).isEqualTo("Updated");
  }

  @Test
  void updateSubtaskStateTargetsPlanById() {
    String sessionId = "session-2";
    Session session = buildSession(sessionId);
    ToolContext toolContext = buildToolContext(session);

    CreatePlanTool createPlanTool = new CreatePlanTool();
    UpdateTaskTool updateTaskTool = new UpdateTaskTool();
    createPlanTool.createPlan(
        toolContext,
        null,
        "Root",
        "Desc",
        "Outcome",
        List.of(Map.of("name", "Task", "description", "Task desc", "expected_outcome", "Done")));

    Plan plan = loadPlan(session);
    Plan subtask = plan.getSubtasks().get(0);

    updateTaskTool.execute(toolContext, subtask.getId(), "DONE", "finished");

    Plan updated = loadPlan(session);
    Plan updatedSubtask = updated.getSubtasks().get(0);
    assertThat(updatedSubtask.getStatus()).isEqualTo(PlanStatus.DONE);
    assertThat(updatedSubtask.getOutcome()).isEqualTo("finished");
  }

  private static Session buildSession(final String sessionId) {
    return Session.builder(sessionId)
        .appName("agent")
        .userId("default")
        .state(new ConcurrentHashMap<>())
        .events(new ArrayList<>())
        .build();
  }

  private static ToolContext buildToolContext(final Session session) {
    InvocationContext invocationContext = InvocationContext.builder().session(session).build();
    return ToolContext.builder(invocationContext).build();
  }

  private static Plan loadPlan(final Session session) {
    return (Plan) session.state().get(PlanningUtils.PLAN_STATE_KEY);
  }
}
