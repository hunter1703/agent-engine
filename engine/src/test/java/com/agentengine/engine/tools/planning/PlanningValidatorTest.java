package com.agentengine.engine.tools.planning;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.tools.planning.beans.PlanStatus;
import com.agentengine.engine.tools.planning.beans.Task;
import com.agentengine.engine.tools.planning.beans.TaskStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanningValidatorTest {

  @Test
  void blocksFinalAnswerWhenPlanNotTerminal() {
    final Plan plan = new Plan("Plan", "Goal");
    plan.setPlanId("plan-1");
    plan.setStatus(PlanStatus.IN_PROGRESS);
    final Task task = new Task("Task", "Goal");
    task.setTaskId("task-1");
    task.setStatus(TaskStatus.DONE);
    plan.setTasks(List.of(task));

    final String error = PlanningValidator.canSubmitFinalAnswerOrError(plan);

    assertThat(error).contains("Cannot submit final answer").contains("in_progress");
  }

  @Test
  void allowsFinalAnswerWhenPlanTerminal() {
    final Plan plan = new Plan("Plan", "Goal");
    plan.setPlanId("plan-1");
    plan.setStatus(PlanStatus.DONE);

    final String error = PlanningValidator.canSubmitFinalAnswerOrError(plan);

    assertThat(error).isNull();
  }
}
