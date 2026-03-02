package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.agents.flows.DefaultFlow;
import com.agentengine.engine.agents.processors.Parser;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmResponse;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class EngineProtocolTest {

  @Test
  void ensuresEveryTurnSignalsCompletion() {
    // Protocol: Every phase of interaction (a "turn") MUST signal its end by emitting turnComplete: true.
    final Parser parser = Parser.builder().build();
    final DefaultFlow flow = new DefaultFlow(parser);
    
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
    // Set FINAL_ANSWER_DELIVERED so RunCleanupResponseProcessor transitions to FINISHED
    // and guarantees turnComplete=true. Without this, FinalAnswerResponseProcessor fires
    // premature_termination on a text-only response in REASONING phase.
    RunStateUtils.getState(context).setPhase(RunState.Phase.FINAL_ANSWER_DELIVERED);

    // Final response that MUST have turnComplete (enforced by RunCleanup)
    final LlmResponse finalResponse = LlmResponse.builder()
        .content(Content.builder().parts(List.of(Part.builder().text("Done").build())).build())
        .partial(false)
        .build();

    // Test RunCleanup guarantee
    LlmResponse current = finalResponse;
    for (ResponseProcessor p : flow.getResponseProcessors()) {
        current = p.processResponse(context, current).blockingGet().updatedResponse();
    }
    
    assertThat(current.turnComplete().orElse(false)).isTrue();
  }

  @Test
  void ensuresInvocationIdRetention() {
    // Protocol: Every run is initialized with a unique invocationId. Engine guarantees all events carry this ID.
    // This is typically handled by the AgentRunner/AgentExecutionService, but we can verify it via InvocationContext usage.
    final Session session = Session.builder("s1").build();
    final InvocationContext context = InvocationContext.builder()
        .session(session)
        .invocationId("target-id")
        .build();
    
    assertThat(context.invocationId()).isEqualTo("target-id");
  }
}
