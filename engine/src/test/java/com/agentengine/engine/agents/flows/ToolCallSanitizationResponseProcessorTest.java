package com.agentengine.engine.agents.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.agents.processors.response.ToolCallSanitizationResponseProcessor;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.tools.planning.beans.Task;
import com.agentengine.engine.utils.RunStateUtils;
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

class ToolCallSanitizationResponseProcessorTest {

  private ToolCallSanitizationResponseProcessor processor;
  private InvocationContext context;

  @BeforeEach
  void setUp() {
    processor = ToolCallSanitizationResponseProcessor.INSTANCE;
    final Plan plan = new Plan("Root", "Goal");
    final Task task = new Task("Step", "Goal");
    task.setTaskId("task-1");
    plan.setTasks(List.of(task));

    final Session session =
        Session.builder("session-1")
            .appName("agent")
            .userId("default")
            .state(new ConcurrentHashMap<>())
            .events(new ArrayList<>())
            .build();

    context = InvocationContext.builder().session(session).build();
    RunStateUtils.getState(context).updatePlan(plan);
  }

  @Test
  void detectsRedundantToolCalls() {
    final LlmResponse response =
        LlmResponse.builder()
            .content(
                Content.builder()
                    .parts(
                        List.of(
                            Part.builder()
                                .functionCall(
                                    FunctionCall.builder().name("ViewPlanTool").args(Map.of()).build())
                                .build()))
                    .build())
            .build();

    // First time is okay
    processor.processResponse(context, response).blockingGet();

    // Second time with exact same call is redundant
    final ResponseProcessingResult result =
        processor.processResponse(context, response).blockingGet();

    assertThat(result.updatedResponse().turnComplete()).contains(false);
    assertThat(RunStateUtils.getState(context).violations())
        .anyMatch(v -> v.getCorrectionMessage().contains("Redundancy Detected"));
  }

  @Test
  void allowsValidToolCalls() {
    final LlmResponse response =
        LlmResponse.builder()
            .content(
                Content.builder()
                    .parts(
                        List.of(
                            Part.builder()
                                .functionCall(
                                    FunctionCall.builder()
                                        .name("UpdateTaskTool")
                                        .args(Map.of("taskId", "task-1", "status", "DONE"))
                                        .build())
                                .build()))
                    .build())
            .build();

    final ResponseProcessingResult result =
        processor.processResponse(context, response).blockingGet();

    assertThat(result.updatedResponse().partial()).isNotPresent();
    assertThat(RunStateUtils.getState(context).violations()).isEmpty();
  }

  @Test
  void stripsToolPartsFromPartialResponse() {
    final LlmResponse partial =
        LlmResponse.builder()
            .partial(true)
            .content(
                Content.builder()
                    .parts(
                        List.of(
                            Part.builder().text("thinking...").build(),
                            Part.builder()
                                .functionCall(
                                    FunctionCall.builder().name("SomeTool").args(Map.of()).build())
                                .build()))
                    .build())
            .build();

    final ResponseProcessingResult result =
        processor.processResponse(context, partial).blockingGet();

    final List<Part> parts =
        result.updatedResponse().content().flatMap(Content::parts).orElse(List.of());
    assertThat(parts).noneMatch(p -> p.functionCall().isPresent());
    assertThat(parts).anyMatch(p -> p.text().isPresent());
    assertThat(RunStateUtils.getState(context).violations())
        .anyMatch(v -> v.getCode().equals("partial_tool_calls"));
  }
}
