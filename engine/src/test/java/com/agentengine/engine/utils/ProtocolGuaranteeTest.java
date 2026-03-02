package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.agents.flows.DefaultFlow;
import com.agentengine.engine.agents.processors.Parser;
import com.agentengine.engine.tools.SubmitFinalAnswerTool;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.tools.planning.beans.PlanStatus;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmResponse;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class ProtocolGuaranteeTest {

  private final Parser parser = Parser.builder().build();
  private final DefaultFlow flow = new DefaultFlow(parser);

  private InvocationContext createMockContext() {
    final Session session = Session.builder("s1")
        .appName("app")
        .userId("u1")
        .state(new ConcurrentHashMap<>())
        .events(new ArrayList<>())
        .build();
    return InvocationContext.builder()
        .session(session)
        .invocationId("inv-1")
        .build();
  }

  private LlmResponse process(InvocationContext context, LlmResponse response) {
    LlmResponse current = response;
    for (var p : flow.getResponseProcessors()) {
      current = p.processResponse(context, current).blockingGet().updatedResponse();
    }
    return current;
  }

  @Test
  void ensuresFinishReasonSTOPOnCompletion() {
    final InvocationContext context = createMockContext();
    final RunState state = RunStateUtils.getState(context);
    state.setPhase(RunState.Phase.FINAL_ANSWER_DELIVERED);

    final LlmResponse response = LlmResponse.builder()
        .content(Content.builder().parts(List.of(Part.builder().text("Final Answer").build())).build())
        .partial(false)
        .build();

    final LlmResponse updated = process(context, response);

    assertThat(updated.finishReason().get().toString()).contains("STOP");
    assertThat(updated.turnComplete().get()).isTrue();
    assertThat(updated.partial().get()).isFalse();
  }

  @Test
  void ensuresFinishReasonStrippedOnNonDeliveredRuns() {
    final InvocationContext context = createMockContext();
    final RunState state = RunStateUtils.getState(context);
    state.setPhase(RunState.Phase.REASONING);

    final LlmResponse response = LlmResponse.builder()
        .content(Content.builder().parts(List.of(Part.builder().text("Still thinking").build())).build())
        .finishReason(new FinishReason(FinishReason.Known.STOP))
        .partial(false)
        .build();

    final LlmResponse updated = process(context, response);

    assertThat(updated.finishReason()).isEmpty();
  }

  @Test
  void ensuresSimultaneousTextAndFinalAnswerIsViolation() {
    final InvocationContext context = createMockContext();
    final RunState state = RunStateUtils.getState(context);
    
    final FunctionCall finalAnswerCall = FunctionCall.builder()
        .name(SubmitFinalAnswerTool.TOOL_NAME)
        .args(Map.of("answer", "done"))
        .build();
        
    final LlmResponse response = LlmResponse.builder()
        .content(Content.builder().parts(List.of(
            Part.builder().text("Here is my answer").build(),
            Part.builder().functionCall(finalAnswerCall).build()))
            .build())
        .partial(false)
        .build();

    final LlmResponse updated = process(context, response);

    assertThat(state.violations()).anyMatch(v -> "final_answer_protocol".equals(v.getCode()));
    assertThat(updated.turnComplete().get()).isFalse();
  }

  @Test
  void ensuresRedundantToolCallsAreStripped() {
    final InvocationContext context = createMockContext();
    final RunState state = RunStateUtils.getState(context);
    
    final FunctionCall call = FunctionCall.builder().name("tool").args(Map.of("a", "b")).build();
    state.updateLastToolCall("tool{a=b}");

    final LlmResponse response = LlmResponse.builder()
        .content(Content.builder().parts(List.of(Part.builder().functionCall(call).build())).build())
        .partial(false)
        .build();

    final LlmResponse updated = process(context, response);

    assertThat(state.violations()).anyMatch(v -> "redundant_tool_calls".equals(v.getCode()));
    assertThat(updated.content().get().parts().get()).isEmpty();
    assertThat(updated.turnComplete().get()).isFalse();
  }

  @Test
  void ensuresIncompletePlanRejectsFinalAnswer() {
    final InvocationContext context = createMockContext();
    final RunState state = RunStateUtils.getState(context);
    
    final Plan plan = new Plan();
    plan.setStatus(PlanStatus.IN_PROGRESS); // Missing tasks but status is not DONE
    state.updatePlan(plan);

    final LlmResponse response = LlmResponse.builder()
        .content(Content.builder().parts(List.of(
            Part.builder().functionCall(FunctionCall.builder().name(SubmitFinalAnswerTool.TOOL_NAME).build()).build()))
            .build())
        .partial(false)
        .build();

    final LlmResponse updated = process(context, response);

    assertThat(state.violations()).anyMatch(v -> "final_answer_validation".equals(v.getCode()));
    assertThat(updated.turnComplete().get()).isFalse();
  }

  @Test
  void ensuresPartialResponsesStripTools() {
    final InvocationContext context = createMockContext();
    final RunState state = RunStateUtils.getState(context);

    final LlmResponse response = LlmResponse.builder()
        .content(Content.builder().parts(List.of(
            Part.builder().text("Streaming...").build(),
            Part.builder().functionCall(FunctionCall.builder().name("tool").build()).build()))
            .build())
        .partial(true)
        .build();

    final LlmResponse updated = process(context, response);

    assertThat(state.violations()).anyMatch(v -> "partial_tool_calls".equals(v.getCode()));
    assertThat(updated.content().get().parts().get()).hasSize(1);
    assertThat(updated.content().get().parts().get().get(0).text()).isPresent();
  }

  @Test
  void ensuresTagParsing() {
    final InvocationContext context = createMockContext();
    
    // Parser responsibilities per RFC: Parse text/thought tags into structured parts.
    final String raw = "<thought>Thinking about it</thought>Here is the answer";
    final LlmResponse response = LlmResponse.builder()
        .content(Content.builder().parts(List.of(Part.builder().text(raw).build())).build())
        .partial(false)
        .build();

    final LlmResponse updated = process(context, response);
    final List<Part> parts = updated.content().get().parts().get();

    // Canonical order applied: Thought (1), Text (2)
    assertThat(parts).hasSize(2);
    assertThat(parts.get(0).thought().orElse(false)).isTrue();
    assertThat(parts.get(0).text().get()).isEqualTo("Thinking about it");
    assertThat(parts.get(1).thought().orElse(false)).isFalse();
    assertThat(parts.get(1).text().get()).isEqualTo("Here is the answer");
  }

  @Test
  void ensuresCanonicalOrderingOfComplexParts() {
    final InvocationContext context = createMockContext();
    
    // Random input order
    final List<Part> mixedParts = List.of(
        Part.builder().functionCall(FunctionCall.builder().name("tool").build()).build(),
        Part.builder().text("Thinking").thought(true).build(),
        Part.builder().text("Answer").build()
    );

    final LlmResponse response = LlmResponse.builder()
        .content(Content.builder().parts(mixedParts).build())
        .partial(false)
        .build();

    final LlmResponse updated = process(context, response);
    final List<Part> ordered = updated.content().get().parts().get();

    // Verify canonical sequence: Thought (1) -> Text (2) -> Tool Call (3)
    assertThat(ordered.get(0).thought().orElse(false)).isTrue();
    assertThat(ordered.get(1).text().isPresent() && !ordered.get(1).thought().orElse(false)).isTrue();
    assertThat(ordered.get(2).functionCall().isPresent()).isTrue();
  }

  @Test
  void ensuresSanitizationWhenToolsArePresent() {
    final InvocationContext context = createMockContext();
    
    // RFC Guarantee: If ANY tool call is present, all regular text MUST be converted to thoughts.
    final LlmResponse response = LlmResponse.builder()
        .content(Content.builder().parts(List.of(
            Part.builder().text("Processing...").build(),
            Part.builder().functionCall(FunctionCall.builder().name("calc").build()).build()))
            .build())
        .partial(false)
        .build();

    final LlmResponse updated = process(context, response);
    final List<Part> parts = updated.content().get().parts().get();

    // Both should be thoughts/calls, no regular text
    assertThat(parts.get(0).text().get()).isEqualTo("Processing...");
    assertThat(parts.get(0).thought().get()).isTrue();
    assertThat(parts.get(1).functionCall().isPresent()).isTrue();
  }

  @Test
  void ensuresToolCallForcesTurnComplete() {
    final InvocationContext context = createMockContext();
    final LlmResponse response = LlmResponse.builder()
        .content(Content.builder().parts(List.of(
            Part.builder().functionCall(FunctionCall.builder().name("tool").build()).build()))
            .build())
        .partial(false)
        .turnComplete(false) // Protocol should force this to true
        .build();

    final LlmResponse updated = process(context, response);
    assertThat(updated.turnComplete().get()).isTrue();
  }

  @Test
  void ensuresModelStopForcesTurnComplete() {
    final InvocationContext context = createMockContext();
    final LlmResponse response = LlmResponse.builder()
        .content(Content.builder().parts(List.of(Part.builder().text("Done").build())).build())
        .finishReason(new FinishReason(FinishReason.Known.STOP))
        .partial(false)
        .turnComplete(false) // Protocol should force this to true
        .build();

    final LlmResponse updated = process(context, response);
    assertThat(updated.turnComplete().get()).isTrue();
  }
}
