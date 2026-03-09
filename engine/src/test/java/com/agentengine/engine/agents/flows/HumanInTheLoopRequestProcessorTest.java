package com.agentengine.engine.agents.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.agents.processors.request.HumanInTheLoopRequestProcessor;
import com.agentengine.engine.utils.HitlStateUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class HumanInTheLoopRequestProcessorTest {

  @Test
  void appendsResumeContextMessageAndClearsPauseState() {
    final Session session =
        Session.builder("s1")
            .appName("app")
            .userId("user")
            .state(new ConcurrentHashMap<>())
            .events(new ArrayList<>())
            .build();
    final InvocationContext context = InvocationContext.builder().session(session).invocationId("inv-1").build();

    HitlStateUtils.pause(context, "Which cloud region?", "user_clarification");
    final LlmRequest request = LlmRequest.builder().build();

    final LlmRequest updated =
        HumanInTheLoopRequestProcessor.INSTANCE
            .processRequest(context, request)
            .blockingGet()
            .updatedRequest();

    assertThat(HitlStateUtils.isPaused(context)).isFalse();
    assertThat(updated.contents()).isNotNull();
    assertThat(updated.contents()).isNotEmpty();
    final Content resumeContent = updated.contents().get(updated.contents().size() - 1);
    assertThat(resumeContent.role()).contains("user");
    assertThat(resumeContent.text()).contains("Which cloud region?");
    assertThat(resumeContent.text()).contains("Continue the task.");
  }

  @Test
  void resolvesOptionIndexToOptionTextInResumeInstruction() {
    final Session session =
        Session.builder("s1")
            .appName("app")
            .userId("user")
            .state(new ConcurrentHashMap<>())
            .events(new ArrayList<>())
            .build();
    final InvocationContext context = InvocationContext.builder().session(session).invocationId("inv-1").build();

    HitlStateUtils.pause(
        context,
        "Select deployment target",
        List.of("Production", "Staging"),
        "user_clarification");

    final LlmRequest request =
        LlmRequest.builder()
            .contents(List.of(Content.builder().role("user").parts(Part.fromText("2")).build()))
            .build();

    final LlmRequest updated =
        HumanInTheLoopRequestProcessor.INSTANCE
            .processRequest(context, request)
            .blockingGet()
            .updatedRequest();

    assertThat(HitlStateUtils.isPaused(context)).isFalse();
    assertThat(HitlStateUtils.getOptions(context)).isEmpty();
    assertThat(updated.contents()).isNotNull();
    final Content resumeContent = updated.contents().get(updated.contents().size() - 1);
    assertThat(resumeContent.role()).contains("user");
    assertThat(resumeContent.text()).contains("Use this clarification answer: 'Staging'.");
    assertThat(resumeContent.text()).contains("Raw reply was: '2'.");
  }
}
