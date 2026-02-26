package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.tools.planning.PlanningUtils;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.tools.planning.beans.PlanStatus;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor.ResponseProcessingResult;
import com.google.adk.models.LlmResponse;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class FinalAnswerResponseProcessorTest {

  @Test
  void steersTextToThoughtsAndNudgesWhenToolIsMissing() {
    final FinalAnswerResponseProcessor processor = new FinalAnswerResponseProcessor();
    final Session session = Session.builder("s1")
        .appName("app")
        .userId("u1")
        .state(new ConcurrentHashMap<>())
        .events(new ArrayList<>())
        .build();
    final InvocationContext context = InvocationContext.builder()
        .session(session)
        .invocationId("inv-1")
        .build();

    final Content modelContent = Content.builder()
        .role("model")
        .parts(List.of(Part.builder().text("Hello world").build()))
        .build();
    final LlmResponse response = LlmResponse.builder()
        .content(modelContent)
        .turnComplete(true)
        .build();

    final ResponseProcessingResult result = processor.processResponse(context, response).blockingGet();
    final LlmResponse updated = result.updatedResponse();

    // 1. Verify text is now a thought
    final List<Part> parts = updated.content().get().parts().get();
    assertThat(parts).hasSize(1);
    assertThat(parts.get(0).text().get()).isEqualTo("Hello world");
    assertThat(parts.get(0).thought().get()).isTrue();

    // 2. Verify turn is NOT complete according to protocol
    assertThat(updated.turnComplete().get()).isFalse();
    
    // 3. Verify violation is added with resume_needed=true
    final List<Violation> violations = ViolationUtils.getViolations(context);
    assertThat(violations).hasSize(1);
    assertThat(violations.get(0).getCode()).isEqualTo("premature_termination");
    assertThat(violations.get(0).getDetail("resume_needed", false)).isTrue();
  }

  @Test
  void allowsFinalAnswerWhenPlanIsDone() {
    final FinalAnswerResponseProcessor processor = new FinalAnswerResponseProcessor();
    final Session session = Session.builder("s1")
        .appName("app")
        .userId("u1")
        .state(new ConcurrentHashMap<>())
        .events(new ArrayList<>())
        .build();
    final InvocationContext context = InvocationContext.builder()
        .session(session)
        .invocationId("inv-1")
        .build();

    // Setup a completed plan
    final Plan plan = new Plan();
    plan.setStatus(PlanStatus.DONE);
    PlanningUtils.savePlan(context, plan);

    // Turn 1: Signal
    final Content signalContent = Content.builder()
        .role("model")
        .parts(List.of(Part.builder().functionCall(com.google.genai.types.FunctionCall.builder()
            .name(FinalAnswerUtils.TOOL_NAME)
            .build()).build()))
        .build();
    final LlmResponse signalResponse = LlmResponse.builder()
        .content(signalContent)
        .turnComplete(true)
        .build();

    processor.processResponse(context, signalResponse).blockingGet();
    assertThat(FinalAnswerUtils.needsFinalAnswer(context)).isTrue();

    // Turn 2: Actual answer
    final Content answerContent = Content.builder()
        .role("model")
        .parts(List.of(Part.builder().text("Final answer text").build()))
        .build();
    final LlmResponse answerResponse = LlmResponse.builder()
        .content(answerContent)
        .turnComplete(true)
        .build();

    final ResponseProcessingResult result = processor.processResponse(context, answerResponse).blockingGet();
    final LlmResponse updated = result.updatedResponse();

    assertThat(updated.turnComplete().get()).isTrue();
    assertThat(FinalAnswerUtils.needsFinalAnswer(context)).isFalse();
  }
}
