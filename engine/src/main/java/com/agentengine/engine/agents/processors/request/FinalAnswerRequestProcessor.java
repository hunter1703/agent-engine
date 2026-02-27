package com.agentengine.engine.agents.processors.request;

import com.agentengine.engine.api.utils.CorrectionUtils;
import com.agentengine.engine.tools.SubmitFinalAnswerTool;
import com.agentengine.engine.utils.RunState;
import com.agentengine.engine.utils.RunStateUtils;
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

import static com.agentengine.engine.utils.RunState.Phase.FINAL_ANSWER_REQUESTED;

/**
 * Injects the final-answer prompt once readiness is signaled.
 *
 * <p>Responsibilities:
 * - Transition the run from READY → REQUESTED.
 * - Inject synthetic submit-final-answer tool call/response events.
 * - Steer the model to emit only the final answer text.
 *
 * <p>Ownership: final-answer prompt injection and phase progression.
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
    final RunState runState = RunStateUtils.getState(context);

    if (!runState.transitionPhaseIf(RunState.Phase.READY_FOR_FINAL_ANSWER, FINAL_ANSWER_REQUESTED)) {
      return Single.just(RequestProcessingResult.create(request, List.of()));
    }

    final List<Content> contents = new ArrayList<>(request.contents());
    LOG.info("FinalAnswerRequestProcessor: needsFinalAnswer=true, currentContentsSize={}", contents.size());

    // add phantom final answer tool call and response so that model does not get confused if it does not see tool call in context
    final Event toolCallEvent = buildFinalAnswerToolCallEvent(context);
    contents.add(toolCallEvent.content().orElseThrow());

    final Event toolResponseEvent = buildFinalAnswerToolResponseEvent(context, toolCallEvent);
    contents.add(toolResponseEvent.content().orElseThrow());

    final LlmRequest updated = request.toBuilder().contents(contents).build();
    LOG.info("FinalAnswerRequestProcessor: injected 2 synthetic events, newContentsSize={}", updated.contents().size());

    // since tool call and response events are phantom, no need to emit events which would get persisted, just add tor request so that model has access
    return Single.just(RequestProcessingResult.create(updated, List.of()));
  }

  private static Event buildFinalAnswerToolCallEvent(final InvocationContext context) {
    final FunctionCall call = FunctionCall.builder()
        .name(SubmitFinalAnswerTool.TOOL_NAME)
        .id(UUID.randomUUID().toString())
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
        .actions(
            CorrectionUtils.buildCorrectionActions(
                "final_answer", SubmitFinalAnswerTool.TOOL_NAME, FINAL_ANSWER_INSTRUCTION))
        .content(content)
        .build();
  }

  private Event buildFinalAnswerToolResponseEvent(final InvocationContext context, final Event toolCallEvent) {
    final FunctionCall call = toolCallEvent.content()
        .flatMap(Content::parts)
        .map(p -> p.getFirst().functionCall().orElseThrow())
        .orElseThrow();

    final FunctionResponse response = FunctionResponse.builder()
        .name(SubmitFinalAnswerTool.TOOL_NAME)
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
        .actions(
            CorrectionUtils.buildCorrectionActions(
                "final_answer", SubmitFinalAnswerTool.TOOL_NAME, FINAL_ANSWER_INSTRUCTION))
        .content(content)
        .build();
  }
}
