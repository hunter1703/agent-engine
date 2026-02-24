package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.tools.planning.beans.TaskStatus;
import com.agentengine.engine.tools.planning.beans.Task;
import com.agentengine.engine.tools.planning.CreatePlanTool;
import com.agentengine.engine.tools.planning.FinishPlanTool;
import com.agentengine.engine.tools.planning.PlanningUtils;
import com.agentengine.engine.tools.planning.UpdatePlanTool;
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
  void updatePlanTargetsPlanById() {
    String sessionId = "session-1";
    Session session = buildSession(sessionId);
    ToolContext toolContext = buildToolContext(session);

    CreatePlanTool createPlanTool = new CreatePlanTool();
    UpdatePlanTool updatePlanTool = new UpdatePlanTool();
    createPlanTool.execute(
        toolContext,
        "Root",
        "Desc",
        List.of(new Task("Task", "Task goal")));

    updatePlanTool.execute(toolContext, "Updated", null);

    Plan plan = loadPlan(session);
    assertThat(plan.getTitle()).isEqualTo("Updated");
  }

  @Test
  void updateSubtaskStateTargetsPlanById() {
    String sessionId = "session-2";
    Session session = buildSession(sessionId);
    ToolContext toolContext = buildToolContext(session);

    CreatePlanTool createPlanTool = new CreatePlanTool();
    UpdateTaskTool updateTaskTool = new UpdateTaskTool();
    createPlanTool.execute(
        toolContext,
        "Root",
        "Desc",
        List.of(new Task("Task", "Task goal")));

    Plan plan = loadPlan(session);
    Task task = plan.getTasks().get(0);

    updateTaskTool.execute(toolContext, task.getTaskId(), null, null, null, "DONE", "finished");

    Plan updated = loadPlan(session);
    Task updatedTask = updated.getTasks().get(0);
    assertThat(updatedTask.getStatus()).isEqualTo(TaskStatus.DONE);
    assertThat(updatedTask.getResult()).isEqualTo("finished");
  }

  @Test
  void createPlanFailsWhenActivePlanExists() {
    Session session = buildSession("session-3");
    ToolContext toolContext = buildToolContext(session);
    CreatePlanTool createPlanTool = new CreatePlanTool();

    createPlanTool.execute(toolContext, "Root", "Goal", List.of(new Task("T1", "G1")));

    Map<String, Object> response =
        createPlanTool.execute(toolContext, "Second", "Goal", List.of(new Task("T2", "G2")));

    assertThat(response).containsKey("error");
  }

  @Test
  void finishPlanFailsWhenTasksRemainOpen() {
    Session session = buildSession("session-4");
    ToolContext toolContext = buildToolContext(session);
    CreatePlanTool createPlanTool = new CreatePlanTool();
    FinishPlanTool finishPlanTool = new FinishPlanTool();

    createPlanTool.execute(toolContext, "Root", "Goal", List.of(new Task("T1", "G1")));

    Map<String, Object> response = finishPlanTool.execute(toolContext, "done", "done");

    assertThat(response).containsKey("error");
  }

  @Test
  void updateTaskRejectsInvalidStatus() {
    Session session = buildSession("session-5");
    ToolContext toolContext = buildToolContext(session);
    CreatePlanTool createPlanTool = new CreatePlanTool();
    UpdateTaskTool updateTaskTool = new UpdateTaskTool();

    createPlanTool.execute(toolContext, "Root", "Goal", List.of(new Task("T1", "G1")));
    Plan plan = loadPlan(session);
    Task task = plan.getTasks().get(0);

    Map<String, Object> response =
        updateTaskTool.execute(toolContext, task.getTaskId(), null, null, null, "invalid", null);

    assertThat(response).containsKey("error");
  }

  @Test
  void finishPlanRejectsInvalidStatus() {
    Session session = buildSession("session-6");
    ToolContext toolContext = buildToolContext(session);
    CreatePlanTool createPlanTool = new CreatePlanTool();
    FinishPlanTool finishPlanTool = new FinishPlanTool();

    createPlanTool.execute(toolContext, "Root", "Goal", List.of());

    Map<String, Object> response = finishPlanTool.execute(toolContext, "invalid", "done");

    assertThat(response).containsKey("error");
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
