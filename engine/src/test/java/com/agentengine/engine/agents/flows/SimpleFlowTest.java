package com.agentengine.engine.agents.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.artifacts.InMemoryArtifactService;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SimpleFlowTest {

  @Test
  void resetsStepsPerRun() {
    final StubLlm llm = new StubLlm();
    final LlmAgent agent = LlmAgent.builder().name("agent").model(llm).build();
    final Session session = buildSession();
    final SimpleFlow flow = new SimpleFlow(2, List.of(), List.of());

    flow.run(buildContext(agent, session)).toList().blockingGet();
    llm.resetCycle();
    flow.run(buildContext(agent, session)).toList().blockingGet();

    assertThat(llm.totalCalls()).isEqualTo(4);
  }

  private static Session buildSession() {
    final ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
    return Session.builder("session-1")
        .appName("app")
        .userId("user")
        .state(state)
        .events(new ArrayList<>())
        .build();
  }

  private static InvocationContext buildContext(final LlmAgent agent, final Session session) {
    return InvocationContext.builder()
        .agent(agent)
        .session(session)
        .sessionService(new InMemorySessionService())
        .artifactService(new InMemoryArtifactService())
        .build();
  }

  private static final class StubLlm extends BaseLlm {
    private final AtomicInteger totalCalls = new AtomicInteger();
    private final AtomicInteger cycleCalls = new AtomicInteger();

    private StubLlm() {
      super("stub-llm");
    }

    @Override
    public Flowable<LlmResponse> generateContent(
        final LlmRequest llmRequest, final boolean stream) {
      final int current = totalCalls.incrementAndGet();
      final int cycle = cycleCalls.incrementAndGet();
      final boolean partial = cycle <= 2;
      final Content content = Content.fromParts(Part.fromText("step-" + current));
      final LlmResponse response = LlmResponse.builder().content(content).partial(partial).build();
      return Flowable.just(response);
    }

    @Override
    public BaseLlmConnection connect(final LlmRequest llmRequest) {
      return null;
    }

    private int totalCalls() {
      return totalCalls.get();
    }

    private void resetCycle() {
      cycleCalls.set(0);
    }
  }
}
