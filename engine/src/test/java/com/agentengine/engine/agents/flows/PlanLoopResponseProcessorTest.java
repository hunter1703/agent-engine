package com.agentengine.engine.agents.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.agents.processors.response.PlanLoopResponseProcessor;
import com.agentengine.engine.utils.RunStateUtils;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.tools.planning.beans.Task;
import com.agentengine.engine.tools.planning.beans.TaskStatus;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmResponse;
import com.google.adk.sessions.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class PlanLoopResponseProcessorTest {

  @Test
  void leavesResponseUnchangedWhenTasksRemain() {
    Plan plan = new Plan("Root", "Goal");
    Task task = new Task("Step", "Goal");
    task.setStatus(TaskStatus.TODO);
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
    LlmResponse response = LlmResponse.builder().partial(false).turnComplete(true).build();

    PlanLoopResponseProcessor processor = new PlanLoopResponseProcessor();
    LlmResponse updated =
        processor.processResponse(context, response).blockingGet().updatedResponse();

    assertThat(updated.partial().orElse(true)).isFalse();
    assertThat(updated.turnComplete().orElse(true)).isFalse();
  }

  @Test
  void doesNotAbandonTaskWithoutStepLimit() {
    Plan plan = new Plan("Root", "Goal");
    Task task = new Task("Step", "Goal");
    task.setStatus(TaskStatus.IN_PROGRESS);
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
    LlmResponse response = LlmResponse.builder().build();

    PlanLoopResponseProcessor processor = new PlanLoopResponseProcessor();
    processor.processResponse(context, response).blockingGet();

    assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
  }

  @Test
  void doesNotAbandonTaskOnPartialResponses() {
    Plan plan = new Plan("Root", "Goal");
    Task task = new Task("Step", "Goal");
    task.setStatus(TaskStatus.IN_PROGRESS);
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

    // Simulate multi-chunk turn
    LlmResponse partialResponse = LlmResponse.builder().partial(true).build();
    LlmResponse finalResponse = LlmResponse.builder().partial(false).build();

    PlanLoopResponseProcessor processor = new PlanLoopResponseProcessor();

    // First chunk (partial)
    processor.processResponse(context, partialResponse).blockingGet();
    assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);

    // Second chunk (final)
    processor.processResponse(context, finalResponse).blockingGet();
    assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
  }

}
