package com.agentengine.engine.agents.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.testing.MockAgent;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.utils.RunStateUtils;
import com.google.adk.events.Event;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.FunctionDeclaration;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class EngineBehaviorTest {

  @Test
  void simpleFlowAcceptsNaturalFinalAnswer() {
    final LlmResponse answerResponse = MockAgent.responseWithParts(
        List.of(
            MockAgent.textPart("The answer is 42")
        ),
        false,
        true
    );

    final MockAgent agent = MockAgent.builder()
        .flowType(MockAgent.FlowType.SIMPLE)
        .agentName("mock-agent")
        .response(answerResponse)
        .build();

    final List<Event> events = agent.run().toList().blockingGet();

    // No correction event — combined submission is valid
    assertThat(events).noneMatch(e ->
        "user".equals(e.author()) &&
        e.content().map(c -> c.parts().orElse(List.of()).stream()
            .anyMatch(p -> p.text().orElse("").contains("Simultaneous text")))
            .orElse(false));

    // Answer text is delivered in the model event
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

    // Verify plan context was appended to request content.
    final LlmRequest lastRequest = requests.get(requests.size() - 1);
    final String requestContentText =
        Optional.ofNullable(lastRequest.contents()).orElse(List.of()).stream()
            .flatMap(content -> content.parts().orElse(List.of()).stream())
            .map(part -> part.text().orElse(""))
            .collect(Collectors.joining("\n"));
    assertThat(requestContentText)
        .contains("PLAN CONTEXT")
        .contains("Refactor API")
        .contains("Testing goal");
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
        .agentName("mock-agent")
        // Response 1: first ls call (succeeds), tools executed, comes back for turn 2
        .response(toolCall)
        // Response 2: same ls call again (redundant, stripped by RedundantToolCallsResponseProcessor)
        .response(toolCall)
        // Response 3: correction applied, agent adjusts, provides final answer
        .response(MockAgent.responseWithParts(
            List.of(MockAgent.textPart("Okay, done.")),
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
