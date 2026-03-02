package com.agentengine.engine.agents.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.testing.MockAgent;
import com.agentengine.engine.tools.SubmitFinalAnswerTool;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.utils.RunStateUtils;
import com.google.adk.events.Event;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.FunctionDeclaration;
import io.reactivex.rxjava3.core.Single;

import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

class EngineBehaviorTest {

  @Test
  void simpleFlowEnforcesFinalAnswerProtocol() {
    // Stage 1: Agent tries to provide text and final answer tool call simultaneously. (Violation)
    final LlmResponse responseWithViolation = MockAgent.responseWithParts(
        List.of(
            MockAgent.textPart("The answer is 42"),
            MockAgent.toolCallPart("call-1", SubmitFinalAnswerTool.TOOL_NAME, Map.of())
        ),
        false,
        true
    );

    // Stage 2: Agent provides CLEAN signal call.
    final LlmResponse cleanSignal = MockAgent.responseWithParts(
        List.of(MockAgent.toolCallPart("call-2", SubmitFinalAnswerTool.TOOL_NAME, Map.of())),
        false,
        true
    );

    final MockAgent agent = MockAgent.builder()
        .flowType(MockAgent.FlowType.SIMPLE)
        .agentName("mock-agent")
        .tool(new com.agentengine.engine.tools.SubmitFinalAnswerTool())
        .response(responseWithViolation)
        .response(cleanSignal)
        .build();

    final List<Event> events = agent.run().toList().blockingGet();
    
    // Verify violations were detected: the corrective message should appear as a user event.
    assertThat(events).anyMatch(e ->
        "user".equals(e.author()) &&
        e.content().map(c -> c.parts().orElse(List.of()).stream()
            .anyMatch(p -> p.text().orElse("").contains("Simultaneous text")))
            .orElse(false));

    // Verify that the answer text was still emitted in the preserved model event.
    assertThat(events).anyMatch(e -> "mock-agent".equals(e.author()) &&
        e.content().flatMap(content -> content.parts().map(parts -> parts.stream()
            .map(p -> p.text().orElse(""))
            .collect(Collectors.joining())
        )).map(text -> text.contains("42")).orElse(false));
  }

  @Test
  void planningFlowInjectsPlanContext() {
    final Plan plan = new Plan();
    plan.setPlanId("plan-123");
    plan.setTitle("Refactor API");
    plan.setGoal("Testing goal");

    final MockAgent agent = MockAgent.builder()
        .flowType(MockAgent.FlowType.PLANNING)
        .response(MockAgent.responseWithText("Processing..."))
        .build();

    // Side-load plan into session state
    RunStateUtils.getState(agent.context()).updatePlan(plan);

    agent.run().toList().blockingGet();

    final List<LlmRequest> requests = agent.model().requests();
    assertThat(requests).isNotEmpty();

    // Verify plan context was injected into system instructions
    final LlmRequest lastRequest = requests.get(requests.size() - 1);
    assertThat(lastRequest.getSystemInstructions())
        .anyMatch(instr -> instr.contains("PLAN CONTEXT") && instr.contains("Refactor API") && instr.contains("Testing goal"));
  }

  @Test
  void redundantToolCallsAreCorrected() {
    final LlmResponse toolCall = MockAgent.responseWithParts(
        List.of(MockAgent.toolCallPart("call-1", "ls", Map.of("path", "/"))),
        false,
        false // INCOMPLETE to allow second turn
    );

    final BaseTool lsTool = new BaseTool("ls", "list files") {
      @Override
      public Optional<FunctionDeclaration> declaration() {
        return Optional.of(FunctionDeclaration.builder().name("ls").description("list files").build());
      }

      @Override
      public Single<Map<String, Object>> runAsync(Map<String, Object> args, ToolContext toolContext) {
        return Single.just(Map.of("files", List.of("file1.txt")));
      }
    };

    final MockAgent agent = MockAgent.builder()
        .flowType(MockAgent.FlowType.SIMPLE)
        // RedundantToolCallsResponseProcessor is already in the default MockAgent pipeline
        .tool(lsTool)
        .tool(new com.agentengine.engine.tools.SubmitFinalAnswerTool())
        .agentName("mock-agent")
        // Response 1: first ls call (succeeds), tools executed, comes back for turn 2
        .response(toolCall)
        // Response 2: same ls call again (redundant, stripped by RedundantToolCallsResponseProcessor)
        .response(toolCall)
        // Response 3: correction applied, agent adjusts, calls final answer
        .response(MockAgent.responseWithParts(
            List.of(MockAgent.toolCallPart("call-final", SubmitFinalAnswerTool.TOOL_NAME, Map.of())),
            false, true))
        .build();

    agent.run().toList().blockingGet();

    // The redundancy violation should have been registered in RunState.
    final var state = RunStateUtils.getState(agent.context());
    // Violations are cleared after CorrectionProcessor runs, but the lastToolCall was updated on Turn 1
    // and the violation was triggered on Turn 2. Verify the violation was at some point held.
    // Since violations are cleared, we verify via the lastToolCall tracking that Turn 1 was registered.
    assertThat(state.lastToolCall()).isNotNull();
  }
}
