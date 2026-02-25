package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.tools.planning.PlanningUtils;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.tools.planning.beans.PlanStatus;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmResponse;
import com.google.adk.sessions.Session;
import com.google.adk.flows.llmflows.ResponseProcessor.ResponseProcessingResult;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class FinalAnswerResponseProcessorTest {

  @Test
  void marksTextAsThoughtBeforeFinalAnswerSignal() {
    final FinalAnswerResponseProcessor processor = new FinalAnswerResponseProcessor();
    final InvocationContext context = buildContext();
    final Content content =
        Content.builder().role("model").parts(List.of(Part.builder().text("draft").build())).build();
    final LlmResponse response =
        LlmResponse.builder().content(content).partial(false).turnComplete(true).build();

    final LlmResponse updated =
        processor.processResponse(context, response).blockingGet().updatedResponse();

    final List<Part> parts = updated.content().orElseThrow().parts().orElse(List.of());
    assertThat(parts).hasSize(1);
    assertThat(parts.getFirst().thought()).contains(true);
  }

  @Test
  void stripsFinalAnswerSignalAndApproves() {
    final FinalAnswerResponseProcessor processor = new FinalAnswerResponseProcessor();
    final InvocationContext context = buildContext();
    
    // Set up a valid COMPLETED plan so validation passes
    final Plan plan = new Plan("p1", "goal");
    plan.setStatus(PlanStatus.DONE);
    plan.setResult("completed");
    PlanningUtils.savePlan(context, plan);

    final FunctionCall submitCall =
        FunctionCall.builder().id("call-final").name(FinalAnswerUtils.TOOL_NAME).args(Map.of()).build();
    final Content content =
        Content.builder()
            .role("model")
            .parts(List.of(Part.builder().functionCall(submitCall).build()))
            .build();
    final LlmResponse response =
        LlmResponse.builder().content(content).partial(false).turnComplete(true).build();

    final LlmResponse updated =
        processor.processResponse(context, response).blockingGet().updatedResponse();

    final List<Part> parts = updated.content().orElseThrow().parts().orElse(List.of());
    assertThat(parts).isEmpty(); // Signal tool is stripped, no text provided
    assertThat(FinalAnswerUtils.needsFinalAnswer(context)).isTrue();
  }

  @Test
  void allowsSimultaneousTextAndOtherToolsIfNOTFinishing() {
    final FinalAnswerResponseProcessor processor = new FinalAnswerResponseProcessor();
    final InvocationContext context = buildContext();
    
    final FunctionCall otherCall =
        FunctionCall.builder().id("c1").name("other_tool").args(Map.of()).build();
    final Content content =
        Content.builder()
            .role("model")
            .parts(
                List.of(
                    Part.builder().text("some explanation").build(),
                    Part.builder().functionCall(otherCall).build()))
            .build();
    final LlmResponse response =
        LlmResponse.builder().content(content).partial(false).turnComplete(true).build();

    final LlmResponse updated =
        processor.processResponse(context, response).blockingGet().updatedResponse();

    // Violation should NOT be detected because submit_final_answer was NOT called
    assertThat(ViolationUtils.getViolations(context)).isEmpty();
    
    // Instead, the text should be converted to a TRACE/THOUGHT
    final List<Part> parts = updated.content().orElseThrow().parts().orElse(List.of());
    assertThat(parts).anyMatch(p -> p.text().isPresent() && p.thought().orElse(false));
  }

  @Test
  void detectsSimultaneousSubmitAndOtherTools() {
    final FinalAnswerResponseProcessor processor = new FinalAnswerResponseProcessor();
    final InvocationContext context = buildContext();
    
    final FunctionCall submitCall =
        FunctionCall.builder().id("call-final").name(FinalAnswerUtils.TOOL_NAME).args(Map.of()).build();
    final FunctionCall otherCall =
        FunctionCall.builder().id("c1").name("other_tool").args(Map.of()).build();
    final Content content =
        Content.builder()
            .role("model")
            .parts(
                List.of(
                    Part.builder().functionCall(submitCall).build(),
                    Part.builder().functionCall(otherCall).build()))
            .build();
    final LlmResponse response =
        LlmResponse.builder().content(content).partial(false).turnComplete(true).build();

    final LlmResponse updated =
        processor.processResponse(context, response).blockingGet().updatedResponse();

    assertThat(updated.turnComplete()).contains(false);
    assertThat(ViolationUtils.getViolations(context)).hasSize(1);
    assertThat(ViolationUtils.getViolations(context).get(0).getCode()).isEqualTo("final_answer_with_tools");
  }

  @Test
  void detectsSimultaneousSubmitAndText() {
    final FinalAnswerResponseProcessor processor = new FinalAnswerResponseProcessor();
    final InvocationContext context = buildContext();
    
    final FunctionCall submitCall =
        FunctionCall.builder().id("call-final").name(FinalAnswerUtils.TOOL_NAME).args(Map.of()).build();
    final Content content =
        Content.builder()
            .role("model")
            .parts(
                List.of(
                    Part.builder().text("here is the answer").build(),
                    Part.builder().functionCall(submitCall).build()))
            .build();
    final LlmResponse response =
        LlmResponse.builder().content(content).partial(false).turnComplete(true).build();

    final LlmResponse updated =
        processor.processResponse(context, response).blockingGet().updatedResponse();

    assertThat(updated.turnComplete()).contains(false);
    assertThat(ViolationUtils.getViolations(context)).hasSize(1);
    assertThat(ViolationUtils.getViolations(context).get(0).getCode()).isEqualTo("final_answer_with_text");
  }

  @Test
  void stripsSignalToolEvenOnViolation() {
    final FinalAnswerResponseProcessor processor = new FinalAnswerResponseProcessor();
    final InvocationContext context = buildContext();
    
    final FunctionCall submitCall =
        FunctionCall.builder().id("call-final").name(FinalAnswerUtils.TOOL_NAME).args(Map.of()).build();
    final FunctionCall otherCall =
        FunctionCall.builder().id("c1").name("other_tool").args(Map.of()).build();
    final Content content =
        Content.builder()
            .role("model")
            .parts(
                List.of(
                    Part.builder().functionCall(submitCall).build(),
                    Part.builder().functionCall(otherCall).build()))
            .build();
    final LlmResponse response =
        LlmResponse.builder().content(content).partial(false).turnComplete(true).build();

    final LlmResponse updated =
        processor.processResponse(context, response).blockingGet().updatedResponse();

    // Violation should be detected
    assertThat(ViolationUtils.getViolations(context)).hasSize(1);
    assertThat(ViolationUtils.getViolations(context).get(0).getCode()).isEqualTo("final_answer_with_tools");
    
    // Signal tool MUST be stripped from the updated response content
    final List<Part> parts = updated.content().orElseThrow().parts().orElse(List.of());
    assertThat(parts).noneMatch(FinalAnswerUtils::callsFinalAnswerTool);
    assertThat(parts).hasSize(1); // Only other_tool remains
    assertThat(parts.get(0).functionCall().get().name()).contains("other_tool");
  }

  @Test
  void rejectsFinalAnswerIfPlanInvalid() {
    final FinalAnswerResponseProcessor processor = new FinalAnswerResponseProcessor();
    final InvocationContext context = buildContext();
    
    // Explicitly set plan as IN_PROGRESS to trigger violation
    final Plan plan = new Plan("p1", "goal");
    plan.setStatus(PlanStatus.IN_PROGRESS);
    PlanningUtils.savePlan(context, plan);
    
    final FunctionCall submitCall =
        FunctionCall.builder().id("call-final").name(FinalAnswerUtils.TOOL_NAME).args(Map.of()).build();
    final Content content =
        Content.builder()
            .role("model")
            .parts(List.of(Part.builder().functionCall(submitCall).build()))
            .build();
    final LlmResponse response =
        LlmResponse.builder().content(content).partial(false).turnComplete(true).build();

    final LlmResponse updated =
        processor.processResponse(context, response).blockingGet().updatedResponse();

    assertThat(updated.turnComplete()).contains(false);
    assertThat(ViolationUtils.getViolations(context)).hasSize(1);
    assertThat(ViolationUtils.getViolations(context).get(0).getCode()).isEqualTo("final_answer_validation");
  }

  @Test
  void detectsTurnaroundMissingText() {
    final FinalAnswerResponseProcessor processor = new FinalAnswerResponseProcessor();
    final InvocationContext context = buildContext();
    FinalAnswerUtils.markNeedsFinalAnswer(context);

    // Empty response after signal approved
    final LlmResponse response = LlmResponse.builder()
        .content(Content.builder().parts(List.of()).build())
        .partial(false)
        .turnComplete(true)
        .build();

    final ResponseProcessingResult result = processor.processResponse(context, response).blockingGet();

    assertThat(result.updatedResponse().turnComplete()).contains(false);
    assertThat(ViolationUtils.getViolations(context)).hasSize(1);
    assertThat(ViolationUtils.getViolations(context).get(0).getCode()).isEqualTo("final_answer_turnaround_no_text");
  }

  @Test
  void detectsTurnaroundWithTools() {
    final FinalAnswerResponseProcessor processor = new FinalAnswerResponseProcessor();
    final InvocationContext context = buildContext();
    FinalAnswerUtils.markNeedsFinalAnswer(context);

    final FunctionCall toolCall = FunctionCall.builder().id("c1").name("search").args(Map.of()).build();
    final LlmResponse response = LlmResponse.builder()
        .content(Content.builder().parts(List.of(Part.builder().functionCall(toolCall).build())).build())
        .partial(false)
        .turnComplete(true)
        .build();

    final ResponseProcessingResult result = processor.processResponse(context, response).blockingGet();

    assertThat(result.updatedResponse().turnComplete()).contains(false);
    assertThat(ViolationUtils.getViolations(context)).hasSize(1);
    assertThat(ViolationUtils.getViolations(context).get(0).getCode()).isEqualTo("final_answer_turnaround_tools");
  }

  private static InvocationContext buildContext() {
    final Session session =
        Session.builder("session-1")
            .appName("app")
            .userId("user")
            .state(new ConcurrentHashMap<>())
            .events(new ArrayList<>())
            .build();
    return InvocationContext.builder().session(session).invocationId("inv-1").build();
  }
}
