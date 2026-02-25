package com.agentengine.engine.agents.flows;

import com.agentengine.engine.utils.FinalAnswerUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.List;

/**
 * Ensures that if the agent has signaled the final answer, it is strictly steered to provide only the answer.
 */
public final class FinalAnswerRequestProcessor implements RequestProcessor {
  public static final FinalAnswerRequestProcessor INSTANCE = new FinalAnswerRequestProcessor();
  private static final String FINAL_ANSWER_INSTRUCTION = 
      "You have signaled that you are ready to provide the final answer. " +
      "Provide the answer immediately now. " +
      "DO NOT use any more tools. DO NOT provide any further internal reasoning. " +
      "Just provide the final answer to the user.";

  @Override
  public Single<RequestProcessingResult> processRequest(final InvocationContext context, final LlmRequest request) {
    FinalAnswerUtils.syncInvocation(context);
    final boolean needsFinalAnswer = FinalAnswerUtils.needsFinalAnswer(context);

    if (!needsFinalAnswer) {
      return Single.just(RequestProcessingResult.create(request, List.of()));
    }

    final Event correctiveEvent = buildInstructionEvent(context);
    final Content correctionContent =
        correctiveEvent.content().map(content -> content.toBuilder().build()).orElse(null);
    final List<Content> contents = new ArrayList<>(request.contents());
    contents.add(correctionContent);
    final LlmRequest updated = request.toBuilder().contents(contents).build();

    return Single.just(RequestProcessingResult.create(updated, List.of(correctiveEvent)));
  }

  private Event buildInstructionEvent(final InvocationContext context) {
    final Content correctiveContent =
        Content.builder()
            .role("user")
            .parts(List.of(Part.builder().text(FINAL_ANSWER_INSTRUCTION).build()))
            .build();
    return Event.builder()
        .id(Event.generateEventId())
        .invocationId(context.invocationId())
        .author("user")
        .branch(context.branch())
        .content(correctiveContent)
        .build();
  }

}
