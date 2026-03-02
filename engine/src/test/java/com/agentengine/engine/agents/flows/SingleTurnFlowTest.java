package com.agentengine.engine.agents.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.artifacts.InMemoryArtifactService;
import com.google.adk.events.Event;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class SingleTurnFlowTest {

  @Test
  void retriesUntilTurnComplete() {
    final ScriptedLlm model =
        new ScriptedLlm(
            List.of(
                responseWithText("first", false),
                responseWithText("second", true)));

    final LlmAgent agent = LlmAgent.builder().name("agent").model(model).build();
    final Session session = Session.builder("session")
        .appName("app")
        .userId("user")
        .state(new ConcurrentHashMap<>())
        .events(new ArrayList<>())
        .build();
    final InvocationContext context = InvocationContext.builder()
        .agent(agent)
        .session(session)
        .invocationId("invocation")
        .sessionService(new InMemorySessionService())
        .artifactService(new InMemoryArtifactService())
        .build();

    final SingleTurnFlow flow = new SingleTurnFlow(List.of(), List.of());
    final List<Event> events = flow.run(context).toList().blockingGet();

    assertThat(model.callCount()).isEqualTo(2);
    assertThat(events).hasSize(2);
  }

  private static LlmResponse responseWithText(final String text, final boolean turnComplete) {
    final Content content = Content.builder().role("model").parts(List.of(Part.fromText(text))).build();
    return LlmResponse.builder().content(content).partial(false).turnComplete(turnComplete).build();
  }

  private static final class ScriptedLlm extends BaseLlm {
    private final Deque<LlmResponse> responses;
    private int callCount;

    private ScriptedLlm(final List<LlmResponse> responses) {
      super("test-model");
      this.responses = new ArrayDeque<>(responses);
    }

    @Override
    public Flowable<LlmResponse> generateContent(
        final LlmRequest llmRequest, final boolean stream) {
      callCount++;
      final LlmResponse response = responses.pollFirst();
      return response == null ? Flowable.empty() : Flowable.just(response);
    }

    @Override
    public BaseLlmConnection connect(final LlmRequest llmRequest) {
      return null;
    }

    private int callCount() {
      return callCount;
    }
  }
}
