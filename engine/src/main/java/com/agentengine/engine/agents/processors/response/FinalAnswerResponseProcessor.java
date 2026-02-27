package com.agentengine.engine.agents.processors.response;

import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.tools.SubmitFinalAnswerTool;
import com.agentengine.engine.tools.ToolUtils;
import com.agentengine.engine.utils.RunState;
import com.agentengine.engine.utils.RunStateUtils;
import com.agentengine.engine.utils.Violation;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Enforces the final-answer protocol on responses.
 *
 * <p>Responsibilities:
 * - Validate final-answer signaling and turnaround rules.
 * - Strip submit-final-answer tool parts from user-visible output.
 * - Advance run phases for READY/REQUESTED/DELIVERED.
 * - Block output after final answer delivery.
 *
 * <p>Ownership: final-answer protocol and phase transitions.
 */
public final class FinalAnswerResponseProcessor implements ResponseProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(FinalAnswerResponseProcessor.class);
  public static final FinalAnswerResponseProcessor INSTANCE = new FinalAnswerResponseProcessor();

  @Override
  public Single<ResponseProcessingResult> processResponse(final InvocationContext context, final LlmResponse response) {
    final Content content = response.content().orElse(null);
    if (content == null) {
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }

    final List<Part> parts = content.parts().orElse(List.of());
    final boolean callsFinalAnswerTool = parts.stream().anyMatch(ToolUtils::callsFinalAnswerTool);

    final List<Part> remainingParts = new ArrayList<>();
    final List<String> otherTools = new ArrayList<>();
    String textFound = null;
    boolean hasAnyText = false;
    boolean hasThoughtText = false;

    for (Part part : parts) {
      LOG.info("Processing part: text='{}', functionCall='{}', functionResponse='{}', thought='{}'",
          part.text().orElse(null),
          part.functionCall().map(f -> f.name().orElse("unknown")).orElse(null),
          part.functionResponse().map(f -> f.name().orElse("unknown")).orElse(null),
          part.thought().orElse(false));

      if (ToolUtils.callsFinalAnswerTool(part)) {
        continue;
      }
      part.functionCall().ifPresent(call -> otherTools.add(call.name().orElse("unknown")));
      if (part.text().isPresent()) {
        final String text = part.text().orElse("");
        if (StringUtils.isNotBlank(text)) {
          hasAnyText = true;
          if (part.thought().orElse(false)) {
            hasThoughtText = true;
          } else {
            textFound = text;
          }
        }
      }
      remainingParts.add(part);
    }

    final RunState runState = RunStateUtils.getState(context);
    final RunState.Phase phase = runState.phase();
    final boolean readyForFinalAnswer = phase == RunState.Phase.READY_FOR_FINAL_ANSWER;
    final boolean requestedFinalAnswer = phase == RunState.Phase.FINAL_ANSWER_REQUESTED;
    final List<Part> processedParts = requestedFinalAnswer ? remainingParts : applyThoughtMode(remainingParts);
    final LlmResponse processedResponse =
        response.toBuilder().content(content.toBuilder().parts(processedParts).build()).build();
    final String strippedContentSummary = summarizeStrippedContent(parts);
    final String strippedToolSummary = summarizeStrippedTools(parts);

    if (requestedFinalAnswer)  {
      if (response.partial().orElse(false)) {
        runState.addViolation(Violation.builder("final_answer_turnaround_partial")
            .message("Partial response after final answer signal")
            .correctionMessage(appendStrippedDetail(
                "You signaled for a final answer but provided a partial response. The final answer turn must contain the complete answer in one go. Please provide the full answer now.",
                strippedContentSummary))
            .build());
        return Single.just(ResponseProcessingResult.create(
            markIncomplete(stripAllOutput(processedResponse, content)),
            List.of(),
            Optional.empty()));
      }
      if (callsFinalAnswerTool) {
        runState.addViolation(Violation.builder("final_answer_repeat_signal")
            .message("Repeated final answer signal")
            .correctionMessage(appendStrippedDetail(
                "You already called submit_final_answer. Do NOT call it again; provide only the final answer text now.",
                strippedContentSummary))
            .build());
        return Single.just(ResponseProcessingResult.create(
            markIncomplete(stripAllOutput(processedResponse, content)),
            List.of(),
            Optional.empty()));
      }
      if (!otherTools.isEmpty()) {
        final String toolList = otherTools.stream().distinct().collect(Collectors.joining(", "));
        runState.addViolation(Violation.builder("final_answer_turnaround_tools")
            .message("Tool calls in final answer turnaround")
            .correctionMessage(appendStrippedDetail(
                "You attempted to call tools: [" + toolList + "] while providing the final answer. The answer turn must be PURE text. Use this turn ONLY to state the final answer.",
                strippedContentSummary))
            .build());
        return Single.just(ResponseProcessingResult.create(
            markIncomplete(stripAllOutput(processedResponse, content)),
            List.of(),
            Optional.empty()));
      }
      if (hasThoughtText) {
        runState.addViolation(Violation.builder("final_answer_turnaround_thoughts")
            .message("Thought text in final answer turnaround")
            .correctionMessage(appendStrippedDetail(
                "You included internal reasoning after signaling the final answer. The final answer turn must contain ONLY the user-facing answer text. Please provide the final answer now.",
                strippedContentSummary))
            .build());
        return Single.just(ResponseProcessingResult.create(
            markIncomplete(stripAllOutput(processedResponse, content)),
            List.of(),
            Optional.empty()));
      }
      final String text = content.text();
      if (StringUtils.isBlank(text)) {
        runState.addViolation(Violation.builder("final_answer_turnaround_no_text")
            .message("No text in final answer turnaround")
            .correctionMessage(appendStrippedDetail(
                "You signaled for a final answer but failed to provide any text. The final answer turn must contain the answer text. Please provide the actual answer now.",
                strippedContentSummary))
            .build());
        return Single.just(ResponseProcessingResult.create(
            markIncomplete(stripAllOutput(processedResponse, content)),
            List.of(),
            Optional.empty()));
      }
      runState.transitionPhase(RunState.Phase.FINAL_ANSWER_DELIVERED);
      runState.markThinkingClosed();
      return Single.just(ResponseProcessingResult.create(
          processedResponse.toBuilder().turnComplete(true).build(),
          List.of(),
          Optional.empty()));
    }

    if (callsFinalAnswerTool) {
      if (phase != RunState.Phase.REASONING && phase != RunState.Phase.UNKNOWN) {
        runState.addViolation(Violation.builder("final_answer_repeat_signal")
            .message("Repeated final answer signal")
            .correctionMessage(appendStrippedDetail(
                "You already called submit_final_answer. Do NOT call it again; provide only the final answer text now.",
                strippedToolSummary))
            .build());
        return Single.just(ResponseProcessingResult.create(
            markIncomplete(stripToolOutput(processedResponse, content)),
            List.of(),
            Optional.empty()));
      }
      if (response.partial().orElse(false)) {
        runState.addViolation(Violation.builder("final_answer_with_partial")
            .message("Partial response with final answer signal")
            .correctionMessage(appendStrippedDetail(
                "You signaled for a final answer but provided a partial response. The final answer turn must contain the complete answer in one go. Please provide the full answer now.",
                strippedToolSummary))
            .build());
        return Single.just(ResponseProcessingResult.create(
            markIncomplete(stripToolOutput(processedResponse, content)),
            List.of(),
            Optional.empty()));
      }
      if (!otherTools.isEmpty()) {
        final String toolList = otherTools.stream().distinct().collect(Collectors.joining(", "));
        runState.addViolation(Violation.builder("final_answer_with_tools")
            .message("Simultaneous tool calls with final answer")
            .correctionMessage(appendStrippedDetail(
                "You attempted to provide a final answer while simultaneously making other tool calls: [" + toolList + "]. This is not allowed. Please call only " + SubmitFinalAnswerTool.TOOL_NAME + " to signal your intent.",
                strippedToolSummary))
            .build());
        return Single.just(ResponseProcessingResult.create(
            markIncomplete(stripToolOutput(processedResponse, content)),
            List.of(),
            Optional.empty()));
      }

      if (hasAnyText) {
        final String display = textFound == null ? "(text omitted)" : textFound.length() > 200 ? textFound.substring(0, 197) + "..." : textFound;
        runState.addViolation(Violation.builder("final_answer_with_text")
            .message("Simultaneous text with final answer signal")
            .correctionMessage(appendStrippedDetail(
                "You provided text alongside the final answer signal: \"" + display + "\". This signal turn must NOT contain any text. Call " + SubmitFinalAnswerTool.TOOL_NAME + " as a clean turn, and you will be steered to provide the text in the following turn.",
                strippedToolSummary))
            .build());
        return Single.just(ResponseProcessingResult.create(
            markIncomplete(stripToolOutput(processedResponse, content)),
            List.of(),
            Optional.empty()));
      }

      runState.transitionPhase(RunState.Phase.READY_FOR_FINAL_ANSWER);
      runState.markThinkingClosed();
    } else {
      if (readyForFinalAnswer) {
        runState.addViolation(Violation.builder("final_answer_request_pending")
            .message("Final answer request pending")
            .correctionMessage(appendStrippedDetail(
                "You have already signaled readiness for the final answer. Provide the final answer after the system prompt.",
                strippedContentSummary))
            .build());
        return Single.just(ResponseProcessingResult.create(
            markIncomplete(stripAllOutput(processedResponse, content)),
            List.of(),
            Optional.empty()));
      }
      if (hasAnyText && otherTools.isEmpty()) {
        runState.addViolation(Violation.builder("premature_termination")
            .message("Output text without submit_final_answer tool call")
            .correctionMessage("System Reminder: You provided text without calling '" + SubmitFinalAnswerTool.TOOL_NAME + "'. All text BEFORE this tool is treated as internal thought. Continue reasoning or call other tools until you are ready, then call '" + SubmitFinalAnswerTool.TOOL_NAME + "' as a clean turn.")
            .build());
      }
    }
      return Single.just(ResponseProcessingResult.create(
          markIncomplete(processedResponse),
          List.of(),
          Optional.empty()));
  }

  private static List<Part> applyThoughtMode(final List<Part> parts) {
    final List<Part> updatedParts = new ArrayList<>(parts.size());
    for (final Part part : parts) {
      if (part.text().isPresent() && !part.thought().orElse(false)) {
        updatedParts.add(part.toBuilder().thought(true).build());
      } else {
        updatedParts.add(part);
      }
    }
    return updatedParts;
  }

  private static LlmResponse stripAllOutput(final LlmResponse response, final Content content) {
    if (response == null || content == null) {
      return response;
    }
    final Content emptyContent = content.toBuilder().parts(List.of()).build();
    return response.toBuilder().content(emptyContent).build();
  }

  private static LlmResponse stripToolOutput(final LlmResponse response, final Content content) {
    if (response == null || content == null) {
      return response;
    }
    final List<Part> thoughtOnly =
        content.parts().orElse(List.of()).stream()
            .filter(part -> part.functionCall().isEmpty() && part.functionResponse().isEmpty())
            .map(part ->
                part.text().isPresent() && !part.thought().orElse(false)
                    ? part.toBuilder().thought(true).build()
                    : part)
            .toList();
    return response.toBuilder()
        .content(content.toBuilder().parts(thoughtOnly).build())
        .build();
  }

  private static LlmResponse markIncomplete(final LlmResponse response) {
    if (response == null) {
      return null;
    }
    return response.toBuilder().turnComplete(false).build();
  }

  private static String summarizeStrippedContent(final List<Part> parts) {
    if (parts == null || parts.isEmpty()) {
      return "";
    }
    final List<String> texts = new ArrayList<>();
    final List<String> thoughts = new ArrayList<>();
    for (final Part part : parts) {
      if (part.text().isPresent()) {
        final String text = part.text().orElse("");
        if (StringUtils.isNotBlank(text)) {
          if (part.thought().orElse(false)) {
            thoughts.add(text);
          } else {
            texts.add(text);
          }
        }
      }
    }
    final List<String> segments = new ArrayList<>();
    final String textSummary = summarizeTextSegments(texts);
    if (StringUtils.isNotBlank(textSummary)) {
      segments.add("text=\"" + textSummary + "\"");
    }
    final String thoughtSummary = summarizeTextSegments(thoughts);
    if (StringUtils.isNotBlank(thoughtSummary)) {
      segments.add("thoughts=\"" + thoughtSummary + "\"");
    }
    final String toolSummary = summarizeStrippedTools(parts);
    if (StringUtils.isNotBlank(toolSummary)) {
      segments.add(toolSummary);
    }
    return String.join("; ", segments);
  }

  private static String summarizeStrippedTools(final List<Part> parts) {
      return ToolUtils.summarizeToolParts(parts);
  }

  private static String summarizeTextSegments(final List<String> texts) {
    if (texts == null || texts.isEmpty()) {
      return "";
    }
    final String joined = texts.stream()
        .filter(StringUtils::isNotBlank)
        .distinct()
        .collect(Collectors.joining(" | "));
    if (joined.length() <= 200) {
      return joined;
    }
    return joined.substring(0, 197) + "...";
  }

  private static String appendStrippedDetail(final String message, final String detail) {
    if (StringUtils.isBlank(detail)) {
      return message;
    }
    return message + " Stripped content: " + detail + ".";
  }

}
