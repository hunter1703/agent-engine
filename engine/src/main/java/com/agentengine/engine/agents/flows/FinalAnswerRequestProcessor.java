package com.agentengine.engine.agents.flows;

import com.agentengine.engine.utils.FinalAnswerUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ensures that if the agent has signaled the final answer, it is strictly steered to provide only the answer.
 */
public final class FinalAnswerRequestProcessor implements RequestProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(FinalAnswerRequestProcessor.class);
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

    final List<Event> events = new ArrayList<>();
    final List<Content> contents = new ArrayList<>(request.contents());
    LOG.info("FinalAnswerRequestProcessor: needsFinalAnswer=true, currentContentsSize={}", contents.size());

    final Event toolCallEvent = buildFinalAnswerToolCallEvent(context);
    toolCallEvent.content().ifPresent(contents::add);
    events.add(toolCallEvent);

    final Event toolResponseEvent = buildFinalAnswerToolResponseEvent(context, toolCallEvent);
    toolResponseEvent.content().ifPresent(contents::add);
    events.add(toolResponseEvent);

    final LlmRequest updated = request.toBuilder().contents(contents).build();
    LOG.info("FinalAnswerRequestProcessor: injected 2 synthetic events, newContentsSize={}", updated.contents().size());

    return Single.just(RequestProcessingResult.create(updated, List.of()));
  }

  private Event buildFinalAnswerToolCallEvent(final InvocationContext context) {
    final String callId = UUID.randomUUID().toString();
    final FunctionCall call = FunctionCall.builder()
        .name(FinalAnswerUtils.TOOL_NAME)
        .id(callId)
        .args(Map.of())
        .build();
    
    final Content content = Content.builder()
        .role("model")
        .parts(List.of(Part.builder().functionCall(call).build()))
        .build();

    return Event.builder()
        .id(Event.generateEventId())
        .invocationId(context.invocationId())
        .author("model")
        .branch(context.branch())
        .content(content)
        .build();
  }

  private Event buildFinalAnswerToolResponseEvent(final InvocationContext context, final Event toolCallEvent) {
    final FunctionCall call = toolCallEvent.content()
        .flatMap(Content::parts)
        .map(p -> p.getFirst().functionCall().orElseThrow())
        .orElseThrow();

    final FunctionResponse response = FunctionResponse.builder()
        .name(FinalAnswerUtils.TOOL_NAME)
        .id(call.id().orElse(null))
        .response(Map.of("status", "success", "message", FINAL_ANSWER_INSTRUCTION))
        .build();

    final Content content = Content.builder()
        .role("user")
        .parts(List.of(Part.builder().functionResponse(response).build()))
        .build();

    return Event.builder()
        .id(Event.generateEventId())
        .invocationId(context.invocationId())
        .author("user")
        .branch(context.branch())
        .content(content)
        .build();
  }
}
