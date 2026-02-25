package com.agentengine.engine.utils;

import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.tools.planning.PlanningUtils;
import com.agentengine.engine.tools.planning.PlanningValidator;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Handles:
 * 1. Enforcement: Rejects simultaneous text and (non-final) tool calls.
 * 2. Validation: Verifies plan completion before allowing final answer.
 * 3. Formatting: Strips signal tools and manages thought-steering for UI.
 */
public final class FinalAnswerResponseProcessor implements ResponseProcessor {
  public static final FinalAnswerResponseProcessor INSTANCE = new FinalAnswerResponseProcessor();

  @Override
  public Single<ResponseProcessingResult> processResponse(final InvocationContext context, final LlmResponse response) {
    FinalAnswerUtils.syncInvocation(context);

    final Content content = response.content().orElse(null);
    if (content == null) {
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }

    final List<Part> parts = content.parts().orElse(List.of());
    final boolean callsFinalAnswerTool = parts.stream().anyMatch(FinalAnswerUtils::callsFinalAnswerTool);

    final List<Part> remainingParts = new ArrayList<>();
    final List<String> otherTools = new ArrayList<>();
    String textFound = null;

    for (Part part : parts) {
      if (FinalAnswerUtils.callsFinalAnswerTool(part)) {
        continue;
      }
      part.functionCall().ifPresent(call -> otherTools.add(call.name().orElse("unknown")));
      if (part.text().isPresent() && !part.thought().orElse(false)) {
        if (StringUtils.isNotBlank(part.text().get())) {
          textFound = part.text().get();
        }
      }
      remainingParts.add(part);
    }

    if (FinalAnswerUtils.needsFinalAnswer(context))  {
      if (response.partial().orElse(false)) {
        ViolationUtils.addViolation(context, Violation.builder("final_answer_turnaround_partial")
            .message("Partial response after final answer signal")
            .correctionMessage("You signaled for a final answer but provided a partial response. The final answer turn must contain the complete answer in one go. Please provide the full answer now.")
            .build());
        return Single.just(ResponseProcessingResult.create(response.toBuilder().turnComplete(false).build(), List.of(), Optional.empty()));
      }
      if (!otherTools.isEmpty()) {
        final String toolList = otherTools.stream().distinct().collect(Collectors.joining(", "));
        ViolationUtils.addViolation(context, Violation.builder("final_answer_turnaround_tools")
            .message("Tool calls in final answer turnaround")
            .correctionMessage("You attempted to call tools: [" + toolList + "] while providing the final answer. The answer turn must be PURE text. Use this turn ONLY to state the final answer.")
            .build());
        return Single.just(ResponseProcessingResult.create(response.toBuilder().turnComplete(false).build(), List.of(), Optional.empty()));
      }
      final String text = response.content().orElse(Content.builder().build()).text();
      if (StringUtils.isBlank(text)) {
        ViolationUtils.addViolation(context, Violation.builder("final_answer_turnaround_no_text")
            .message("No text in final answer turnaround")
            .correctionMessage("You signaled for a final answer but failed to provide any text. The final answer turn must contain the answer text. Please provide the actual answer now.")
            .build());
        return Single.just(ResponseProcessingResult.create(response.toBuilder().turnComplete(false).build(), List.of(), Optional.empty()));
      }
      FinalAnswerUtils.markFinalAnswerComplete(context);
      return Single.just(ResponseProcessingResult.create(response.toBuilder().turnComplete(true).build(), List.of(), Optional.empty()));
    }

    final LlmResponse strippedResponse = response.toBuilder()
            .content(content.toBuilder().parts(remainingParts).build())
            .turnComplete(false)
            .build();

    if (callsFinalAnswerTool) {

      if (response.partial().orElse(false)) {
        ViolationUtils.addViolation(context, Violation.builder("final_answer_with_partial")
            .message("Partial response with final answer signal")
            .correctionMessage("You signaled for a final answer but provided a partial response. The final answer turn must contain the complete answer in one go. Please provide the full answer now.")
            .build());
        return Single.just(ResponseProcessingResult.create(strippedResponse, List.of(), Optional.empty()));
      }
      if (!otherTools.isEmpty()) {
        final String toolList = otherTools.stream().distinct().collect(Collectors.joining(", "));
        ViolationUtils.addViolation(context, Violation.builder("final_answer_with_tools")
            .message("Simultaneous tool calls with final answer")
            .correctionMessage("You attempted to provide a final answer while simultaneously making other tool calls: [" + toolList + "]. This is not allowed. Please call only " + FinalAnswerUtils.TOOL_NAME + " to signal your intent.")
            .build());
        return Single.just(ResponseProcessingResult.create(strippedResponse, List.of(), Optional.empty()));
      }

      if (textFound != null) {
        final String display = textFound.length() > 200 ? textFound.substring(0, 197) + "..." : textFound;
        ViolationUtils.addViolation(context, Violation.builder("final_answer_with_text")
            .message("Simultaneous text with final answer signal")
            .correctionMessage("You provided answer text alongside the final answer signal: \"" + display + "\". This signal turn must NOT contain the answer. Call " + FinalAnswerUtils.TOOL_NAME + " as a clean turn, and you will be steered to provide the text in the following turn.")
            .build());
        return Single.just(ResponseProcessingResult.create(strippedResponse, List.of(), Optional.empty()));
      }

      final Plan plan = PlanningUtils.getPlanFromContext(context);
      final String planViolation = PlanningValidator.canSubmitFinalAnswerOrError(plan);
      if (planViolation != null) {
        ViolationUtils.addViolation(context, Violation.builder("final_answer_validation")
            .message(planViolation)
            .correctionMessage(planViolation)
            .build());
        return Single.just(ResponseProcessingResult.create(strippedResponse, List.of(), Optional.empty()));
      }

      FinalAnswerUtils.markNeedsFinalAnswer(context);
      return Single.just(ResponseProcessingResult.create(strippedResponse, List.of(), Optional.empty()));
    } else {
      final List<Part> updatedParts = new ArrayList<>();
      for (Part part : parts) {
        if (part.text().isPresent()) {
          if (!part.thought().orElse(false)) {
            updatedParts.add(part.toBuilder().thought(true).build());
            continue;
          }
        }
        updatedParts.add(part);
      }

      final LlmResponse updatedResponse = response.toBuilder()
              .content(content.toBuilder().parts(updatedParts).build()).turnComplete(false)
              .build();
      return Single.just(ResponseProcessingResult.create(updatedResponse, List.of(), Optional.empty()));
    }
  }
}
