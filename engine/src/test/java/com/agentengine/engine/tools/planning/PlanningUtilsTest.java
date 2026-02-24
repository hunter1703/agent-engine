package com.agentengine.engine.tools.planning;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.tools.planning.beans.PlanStatus;
import com.agentengine.engine.tools.planning.beans.Task;
import com.agentengine.engine.tools.planning.beans.TaskStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanningUtilsTest {

  @Test
  void buildPlanSummaryGeneratesHierarchicalOutput() {
    Plan plan = new Plan("Overhaul API", "Goal of the overhaul");
    plan.setPlanId("plan-123");

    Task task1 = new Task("Define Beans", "Create new bean classes");
    task1.setTaskId("task-1");
    
    Task task2 = new Task("Update Tools", "Refactor tools to use new beans");
    task2.setTaskId("task-2");
    
    Task subtask1 = new Task("Fix CreatePlan", "Update CreatePlanTool");
    subtask1.setTaskId("task-3");
    subtask1.setParentId("task-2");
    
    plan.setTasks(List.of(task1, task2, subtask1));
    task2.setStatus(TaskStatus.IN_PROGRESS);

    String summary = PlanningUtils.buildPlanSummary(plan);

    assertThat(summary).contains("PLAN CONTEXT");
    assertThat(summary).contains("Title: Overhaul API");
    assertThat(summary).contains("Goal: Goal of the overhaul");
    
    // Check tree structure in compact view
    assertThat(summary).contains("- [todo] Define Beans (id: task-1, parent: none)");
    assertThat(summary).contains("- [in_progress] Update Tools (id: task-2, parent: none)");
    assertThat(summary).contains("  - [todo] Fix CreatePlan (id: task-3, parent: task-2)");

    // Check Current Focus section
    assertThat(summary).contains("CURRENT FOCUS:");
    assertThat(summary).contains("Update Tools (id: task-2, parent: none) [in_progress]");
  }

  @Test
  void findTaskByIdLocatesNestedTask() {
    Plan plan = new Plan("Root", "Goal");
    Task task1 = new Task("T1", "G1");
    task1.setTaskId("t1");
    Task task2 = new Task("T2", "G2");
    task2.setTaskId("t2");
    task2.setParentId("t1");
    
    plan.setTasks(List.of(task1, task2));

    Task found = PlanningUtils.findTaskById(plan, "t2");
    assertThat(found).isNotNull();
    assertThat(found.getName()).isEqualTo("T2");
  }

  @Test
  void buildTaskFocusPromptHighlightsOpenTasks() {
    Plan plan = new Plan("Plan", "Goal");
    Task task1 = new Task("T1", "G1");
    task1.setTaskId("t1");
    task1.setStatus(TaskStatus.IN_PROGRESS);
    Task task2 = new Task("T2", "G2");
    task2.setTaskId("t2");
    plan.setTasks(List.of(task1, task2));

    String prompt = PlanningUtils.buildTaskFocusPrompt(plan);

    assertThat(prompt).contains("TASK LOOP");
    assertThat(prompt).contains("FOCUS TASKS");
    assertThat(prompt).contains("t1");
    assertThat(prompt).contains("t2");
  }

  @Test
  void hasOpenTasksHonorsPlanStatus() {
    Plan plan = new Plan("Plan", "Goal");
    plan.setStatus(PlanStatus.DONE);
    plan.setTasks(List.of(new Task("T1", "G1")));

    assertThat(PlanningUtils.hasOpenTask(plan)).isFalse();
  }
}
