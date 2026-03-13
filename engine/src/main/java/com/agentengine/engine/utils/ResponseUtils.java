package com.agentengine.engine.utils;

import com.agentengine.engine.hitl.ConfirmationDecision;
import com.agentengine.engine.hitl.SessionPauseKind;
import com.agentengine.engine.tools.HumanInTheLoopTool;
import com.agentengine.util.common.StringUtils;
import com.google.adk.flows.llmflows.Functions;
import com.google.adk.flows.llmflows.ResponseProcessor.ResponseProcessingResult;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Single;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared flow helpers for event termination and response continuation shaping.
 */
public final class ResponseUtils {

  private ResponseUtils() {
  }

  /* Mirrors {@code Event.finalResponse()} semantics at the LLM-response level. */
  public static boolean isFinalAnswer(final LlmResponse response) {
    if (!hasVisibleText(response)) {
      return false;
    }
    if (response.partial().orElse(false)) {
      return false;
    }
    final Content content = response.content().orElse(null);
    if (content == null) {
      return true;
    }
    final List<Part> parts = content.parts().orElse(List.of());
    final boolean hasFunctionPayloads = parts.stream()
        .anyMatch(part -> part.functionCall().isPresent() || part.functionResponse().isPresent());
    return !hasFunctionPayloads;
  }

  public static Single<ResponseProcessingResult> single(final LlmResponse response) {
    return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
  }

  private static boolean hasVisibleText(final LlmResponse response) {
    if (response == null || response.content().isEmpty()) {
      return false;
    }
    return ContentUtils.hasVisibleText(response.content().orElse(null));
  }

  // the shape must match exactly com.google.adk.events.ToolConfirmation as adk
  // deserializes map
  // into it
  public static Map<String, Object> buildResumeResponse(final SessionPauseKind pauseKind, final ConfirmationDecision decision,
      final String answer) {
    final Map<String, Object> response = new LinkedHashMap<>();
    switch (pauseKind == null ? SessionPauseKind.UNKNOWN : pauseKind) {
      case DECISION -> {
        if (decision == ConfirmationDecision.UNKNOWN) {
          throw new IllegalArgumentException("decision is required for this confirmation");
        }
        response.put("confirmed", decision == ConfirmationDecision.ALLOW);
      }
      case TEXT -> {
        if (StringUtils.isBlank(answer)) {
          throw new IllegalArgumentException("answer is required for this confirmation");
        }
        response.put("confirmed", true);
        response.put("payload", Map.of("answer", answer));
      }
      case UNKNOWN -> throw new IllegalArgumentException("Unknown confirmation type");
    }
    return response;
  }

  public static LlmResponse requestHumanInTheLoop(final String prompt) {
    final String message = StringUtils.isBlank(prompt) ? "User confirmation is required to continue." : prompt.trim();
    final FunctionCall functionCall = FunctionCall.builder().id(Functions.generateClientFunctionCallId()).name(HumanInTheLoopTool.TOOL_NAME)
        .args(Map.of(HumanInTheLoopTool.ARG_PROMPT, message, HumanInTheLoopTool.ARG_KIND, SessionPauseKind.DECISION.name(),
            HumanInTheLoopTool.ARG_OPTIONS, HumanInTheLoopTool.DECISION_OPTIONS))
        .build();
    final Content content = Content.builder().role("model").parts(List.of(Part.builder().functionCall(functionCall).build())).build();
    return LlmResponse.builder().content(content).turnComplete(true).build();
  }
}
