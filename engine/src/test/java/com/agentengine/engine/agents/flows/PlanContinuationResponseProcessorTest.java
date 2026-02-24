package com.agentengine.engine.agents.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.tools.planning.PlanningUtils;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.tools.planning.beans.Task;
import com.agentengine.engine.tools.planning.beans.TaskStatus;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmResponse;
import com.google.adk.sessions.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class PlanContinuationResponseProcessorTest {

  @Test
  void marksResponsePartialWhenTasksRemain() {
    Plan plan = new Plan("Root", "Goal");
    Task task = new Task("Step", "Goal");
    task.setStatus(TaskStatus.TODO);
    plan.setTasks(List.of(task));

    Session session =
        Session.builder("session-1")
            .appName("agent")
            .userId("default")
            .state(new ConcurrentHashMap<>(Map.of(PlanningUtils.PLAN_STATE_KEY, plan)))
            .events(new ArrayList<>())
            .build();

    InvocationContext context = InvocationContext.builder().session(session).build();
    LlmResponse response = LlmResponse.builder().build();

    PlanContinuationResponseProcessor processor = new PlanContinuationResponseProcessor();
    LlmResponse updated =
        processor.processResponse(context, response).blockingGet().updatedResponse();

    assertThat(updated.partial().orElse(false)).isTrue();
  }
}
