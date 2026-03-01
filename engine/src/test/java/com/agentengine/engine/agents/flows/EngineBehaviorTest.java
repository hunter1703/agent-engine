package com.agentengine.engine.agents.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.agents.processors.response.RedundantToolCallsResponseProcessor;
import com.agentengine.engine.testing.MockAgent;
import com.agentengine.engine.tools.SubmitFinalAnswerTool;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.utils.RunStateUtils;
import com.google.adk.events.Event;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionDeclaration;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

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
        .response(responseWithViolation)
        .response(cleanSignal)
        .build();

    final List<Event> events = agent.run().toList().blockingGet();
    
    // Debug output
    events.forEach(e -> System.out.println("EVENT: " + e.author() + " - " + e.content().map(EngineBehaviorTest::extractText).orElse("no content")));

    // Verify violations were detected and corrective user message was injected.
    assertThat(events).anyMatch(e -> "user".equals(e.author()) && 
        e.content().flatMap(content -> content.parts().map(parts -> parts.stream()
            .map(p -> p.text().orElse(""))
            .collect(Collectors.joining())
        )).map(text -> text.contains("alongside")).orElse(false));
    
    // Verify that the answer text was at least emitted (even if marked as thought due to violation).
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

    // Verify system instructions contain the plan summary (Title and Goal).
    final String lastInstructions = requests.get(0).getSystemInstructions().stream()
        .collect(Collectors.joining(" "));
    assertThat(lastInstructions).contains("Refactor API").contains("Testing goal");
  }

  @Test
  void redundantToolCallsAreCorrected() {
    final LlmResponse toolCall = MockAgent.responseWithParts(
        List.of(MockAgent.toolCallPart("call-1", "ls", Map.of("path", "/"))),
        false,
        true
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
        .responseProcessors(List.of(new RedundantToolCallsResponseProcessor()))
        .tool(lsTool)
        .agentName("mock-agent")
        // Two consecutive identical tool calls
        .response(toolCall)
        .response(toolCall) 
        .response(MockAgent.responseWithText("Stopped"))
        .build();

    final List<Event> events = agent.run().toList().blockingGet();

    events.forEach(e -> {
        System.out.println("REDUNDANT EVENT: " + e.author() + " - " + e.content().map(EngineBehaviorTest::extractText).orElse(e.actions().toString()));
        System.out.println("FULL EVENT: " + e);
    });

    // Verify redundancy violation
    assertThat(events).anyMatch(e -> "user".equals(e.author()) && 
        e.content().flatMap(content -> content.parts().map(parts -> parts.stream()
            .map(p -> p.text().orElse(""))
            .collect(Collectors.joining())
        )).map(text -> text.contains("Redundancy Detected")).orElse(false)
        || e.actions() != null && e.actions().stateDelta() != null && e.actions().stateDelta().toString().contains("redundant_tool_calls")
        );
  }



  private static String extractText(Content content) {
    return content.parts().orElse(List.of()).stream()
        .map(p -> p.text().orElse(""))
        .collect(Collectors.joining());
  }
}
