package com.agentengine.engine.agents.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.tools.planning.PlanningUtils;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.tools.planning.beans.Task;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor.ResponseProcessingResult;
import com.google.adk.models.LlmResponse;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RedundantToolCallsResponseProcessorTest {

  private RedundantToolCallsResponseProcessor processor;
  private InvocationContext context;
  private Session session;
  private Plan plan;

  @BeforeEach
  void setUp() {
    processor = new RedundantToolCallsResponseProcessor();
    plan = new Plan("Root", "Goal");
    Task task = new Task("Step", "Goal");
    task.setTaskId("task-1");
    plan.setTasks(List.of(task));

    session = Session.builder("session-1")
        .appName("agent")
        .userId("default")
        .state(new ConcurrentHashMap<>(Map.of(PlanningUtils.PLAN_STATE_KEY, plan)))
        .events(new ArrayList<>())
        .build();

    context = InvocationContext.builder().session(session).build();
  }

  @Test
  void detectsHallucinatedTaskId() {
    LlmResponse response = LlmResponse.builder()
        .content(Content.builder()
            .parts(List.of(Part.builder()
                .functionCall(FunctionCall.builder()
                    .name("UpdateTaskTool")
                    .args(Map.of("taskId", "fake-task", "status", "DONE"))
                    .build())
                .build()))
            .build())
        .build();

    ResponseProcessingResult result = processor.processResponse(context, response).blockingGet();

    assertThat(result.updatedResponse().partial()).isPresent().hasValue(true);
    assertThat(result.updatedResponse().turnComplete()).isPresent().hasValue(false);
    assertThat(PlanningUtils.getViolationMessage(context))
        .contains("Hallucination Detected")
        .contains("fake-task");
  }

  @Test
  void detectsRedundantToolCalls() {
    LlmResponse response = LlmResponse.builder()
        .content(Content.builder()
            .parts(List.of(Part.builder()
                .functionCall(FunctionCall.builder()
                    .name("ViewPlanTool")
                    .args(Map.of())
                    .build())
                .build()))
            .build())
        .build();

    // First time is okay
    processor.processResponse(context, response).blockingGet();
    
    // Second time with exact same call is redundant
    ResponseProcessingResult result = processor.processResponse(context, response).blockingGet();

    assertThat(result.updatedResponse().partial()).isPresent().hasValue(true);
    assertThat(PlanningUtils.getViolationMessage(context))
        .contains("Redundancy Detected");
  }

  @Test
  void allowsValidToolCalls() {
    LlmResponse response = LlmResponse.builder()
        .content(Content.builder()
            .parts(List.of(Part.builder()
                .functionCall(FunctionCall.builder()
                    .name("UpdateTaskTool")
                    .args(Map.of("taskId", "task-1", "status", "DONE"))
                    .build())
                .build()))
            .build())
        .build();

    ResponseProcessingResult result = processor.processResponse(context, response).blockingGet();

    assertThat(result.updatedResponse().partial()).isNotPresent();
    assertThat(PlanningUtils.getViolationMessage(context)).isNull();
  }
}
