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
    assertThat(prompt).doesNotContain("t2");
  }

  @Test
  void hasOpenTasksHonorsPlanStatus() {
    Plan plan = new Plan("Plan", "Goal");
    plan.setStatus(PlanStatus.DONE);
    plan.setTasks(List.of(new Task("T1", "G1")));

    assertThat(PlanningUtils.hasOpenTask(plan)).isFalse();
  }

  @Test
  void getOpenTaskPrefersDepthFirstOrder() {
    Plan plan = new Plan("Plan", "Goal");
    Task root = new Task("Root", "G1");
    root.setTaskId("root");
    root.setStatus(TaskStatus.DONE);
    Task child = new Task("Child", "G2");
    child.setTaskId("child");
    child.setParentId("root");
    Task sibling = new Task("Sibling", "G3");
    sibling.setTaskId("sibling");
    plan.setTasks(List.of(root, child, sibling));

    Task openTask = PlanningUtils.getOpenTask(plan);

    assertThat(openTask).isNotNull();
    assertThat(openTask.getTaskId()).isEqualTo("child");
  }

  @Test
  void canUpdateTaskBlocksMultipleLineages() {
    Plan plan = new Plan("Plan", "Goal");
    Task root = new Task("Root", "G1");
    root.setTaskId("root");
    root.setStatus(TaskStatus.IN_PROGRESS);
    Task branchA = new Task("BranchA", "G2");
    branchA.setTaskId("a");
    branchA.setParentId("root");
    branchA.setStatus(TaskStatus.IN_PROGRESS);
    Task branchB = new Task("BranchB", "G3");
    branchB.setTaskId("b");
    branchB.setParentId("root");
    plan.setTasks(List.of(root, branchA, branchB));

    String error = plan.canUpdateTask(branchB, TaskStatus.IN_PROGRESS);

    assertThat(error).contains("lineage");
  }

  @Test
  void canUpdateTaskRequiresNextTodoInDfsOrder() {
    Plan plan = new Plan("Plan", "Goal");
    Task root = new Task("Root", "G1");
    root.setTaskId("root");
    root.setStatus(TaskStatus.IN_PROGRESS);
    Task child = new Task("Child", "G2");
    child.setTaskId("child");
    child.setParentId("root");
    Task sibling = new Task("Sibling", "G3");
    sibling.setTaskId("sibling");
    plan.setTasks(List.of(root, sibling, child));

    String error = plan.canUpdateTask(sibling, TaskStatus.IN_PROGRESS);

    assertThat(error).contains("Next task to start");
  }

  @Test
  void canUpdateTaskRequiresParentInProgress() {
    Plan plan = new Plan("Plan", "Goal");
    Task parent = new Task("Parent", "G1");
    parent.setTaskId("parent");
    Task child = new Task("Child", "G2");
    child.setTaskId("child");
    child.setParentId("parent");
    plan.setTasks(List.of(parent, child));

    String error = plan.canUpdateTask(child, TaskStatus.IN_PROGRESS);

    assertThat(error).contains("Parent task");
  }

  @Test
  void canUpdateTaskRequiresTerminalChildren() {
    Plan plan = new Plan("Plan", "Goal");
    Task parent = new Task("Parent", "G1");
    parent.setTaskId("parent");
    parent.setStatus(TaskStatus.IN_PROGRESS);
    Task child = new Task("Child", "G2");
    child.setTaskId("child");
    child.setParentId("parent");
    plan.setTasks(List.of(parent, child));

    String error = plan.canUpdateTask(parent, TaskStatus.DONE);

    assertThat(error).contains("child tasks");
  }

  @Test
  void canUpdateTaskBlocksInProgressToTodo() {
    Plan plan = new Plan("Plan", "Goal");
    Task task = new Task("Task", "Goal");
    task.setTaskId("task");
    task.setStatus(TaskStatus.IN_PROGRESS);
    plan.setTasks(List.of(task));

    String error = plan.canUpdateTask(task, TaskStatus.TODO);

    assertThat(error).contains("in_progress");
  }
}
