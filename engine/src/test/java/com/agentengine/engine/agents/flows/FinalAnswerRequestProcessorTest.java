package com.agentengine.engine.agents.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.agents.processors.request.FinalAnswerRequestProcessor;
import com.agentengine.engine.api.utils.CorrectionUtils;
import com.agentengine.engine.utils.RunState;
import com.agentengine.engine.utils.RunStateUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.models.LlmRequest;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class FinalAnswerRequestProcessorTest {

  @Test
  void injectsCorrectiveFinalAnswerEvents() {
    final FinalAnswerRequestProcessor processor = new FinalAnswerRequestProcessor();
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

    RunStateUtils.getState(context).setPhase(RunState.Phase.READY_FOR_FINAL_ANSWER);

    final LlmRequest request = LlmRequest.builder().build();
    final var result = processor.processRequest(context, request).blockingGet();

    final List<Content> contents = result.updatedRequest().contents();
    assertThat(contents).hasSize(2);
    assertThat(contents.get(0).parts().orElse(List.of()))
        .anyMatch(part -> part.functionCall().isPresent());
    assertThat(contents.get(1).parts().orElse(List.of()))
        .anyMatch(part -> part.functionResponse().isPresent());

    final List<Event> events =
        StreamSupport.stream(result.events().spliterator(), false).toList();
    assertThat(events).hasSize(2);
    assertThat(events).allMatch(CorrectionUtils::isCorrectionEvent);
  }
}
