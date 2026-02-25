package com.agentengine.engine.agents.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.tools.planning.PlanningUtils;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.tools.planning.beans.Task;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.sessions.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class PlanTaskRequestProcessorTest {

  @Test
  void appendsTaskFocusPrompt() {
    Plan plan = new Plan("Root", "Goal");
    Task task = new Task("Step", "Goal");
    task.setTaskId("task-1");
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
    PlanTaskRequestProcessor processor = new PlanTaskRequestProcessor();

    LlmRequest updated = processor.processRequest(context, request).blockingGet().updatedRequest();

    assertThat(updated.getSystemInstructions()).isEmpty();
    assertThat(updated.contents()).isNotEmpty();
    
    com.google.genai.types.Content lastContent = updated.contents().get(updated.contents().size() - 1);
    assertThat(lastContent.role()).isPresent().hasValue("user");
    
    String contentText = lastContent.parts().orElse(List.of()).stream()
        .map(p -> p.text().orElse(""))
        .collect(java.util.stream.Collectors.joining());
        
    assertThat(contentText).contains("STRUCTURAL ANCHOR").contains("task-1");
  }

  @Test
  void includesViolationNoticeOnError() {
    Plan plan = new Plan("Root", "Goal");
    Task task = new Task("Step", "Goal");
    task.setTaskId("task-1");
    plan.setTasks(List.of(task));

    Session session = Session.builder("session-1")
            .appName("agent")
            .userId("default")
            .state(new ConcurrentHashMap<>(Map.of(
                PlanningUtils.PLAN_STATE_KEY, plan
            )))
            .events(new ArrayList<>())
            .build();
    
    // Simulate a violation recorded in session state
    session.state().put("plan.verification.violation_message", "Hallucination Detected");

    InvocationContext context = InvocationContext.builder().session(session).build();
    LlmRequest request = LlmRequest.builder().build();

    PlanTaskRequestProcessor processor = new PlanTaskRequestProcessor();
    LlmRequest updated = processor.processRequest(context, request).blockingGet().updatedRequest();

    com.google.genai.types.Content lastContent = updated.contents().get(updated.contents().size() - 1);
    String contentText = lastContent.parts().orElse(List.of()).stream()
        .map(p -> p.text().orElse(""))
        .collect(java.util.stream.Collectors.joining());

    assertThat(contentText).contains("REJECTION NOTICE").contains("Hallucination Detected");
    assertThat(session.state().get("plan.verification.violation_message")).isNull(); // Verify it was cleared
  }
}
