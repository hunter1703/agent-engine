package com.agentengine.runtime.utils;

import com.agentengine.runtime.hitl.SessionPauseKind;
import com.agentengine.runtime.tools.HumanInTheLoopTool;
import com.agentengine.util.common.StringUtils;
import com.google.adk.events.ToolConfirmation;
import com.google.adk.flows.llmflows.Functions;
import com.google.adk.flows.llmflows.ResponseProcessor.ResponseProcessingResult;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Single;
import java.util.*;

/** Shared flow helpers for event termination and response continuation shaping. */
public final class ResponseUtils {

    private ResponseUtils() {}

    /* Mirrors {@code Event.finalResponse()} semantics at the LLM-response level. */
    public static boolean isFinalAnswer(final LlmResponse response) {
        if (!hasVisibleText(response) || response.partial().orElse(false)) {
            return false;
        }
        final List<Part> parts = response.content()
                .map(content -> content.parts().orElse(List.of()))
                .orElse(List.of());
        return parts.stream()
                .noneMatch(part -> part.functionCall().isPresent()
                        || part.functionResponse().isPresent());
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

    public static ToolConfirmation buildToolConfirmation(final Boolean confirmed, final String answer) {
        if (confirmed == null && StringUtils.isBlank(answer)) {
            throw new IllegalArgumentException("either confirmation or answer is required");
        }
        final ToolConfirmation.Builder builder = ToolConfirmation.builder();
        if (confirmed != null) {
            builder.confirmed(confirmed);
        } else if (StringUtils.isNotBlank(answer)) {
            builder.confirmed(true).payload(Map.of("answer", answer));
        }
        return builder.build();
    }

    public static LlmResponse requestHumanToDecide(final String prompt) {
        final String message =
                StringUtils.isBlank(prompt) ? "User confirmation is required to continue." : prompt.trim();
        final FunctionCall functionCall = FunctionCall.builder()
                .id(Functions.generateClientFunctionCallId())
                .name(HumanInTheLoopTool.TOOL_NAME)
                .args(Map.of(
                        HumanInTheLoopTool.PROMPT, message, HumanInTheLoopTool.KIND, SessionPauseKind.DECISION.name()))
                .build();
        final Content content = Content.builder()
                .role("model")
                .parts(List.of(Part.builder().functionCall(functionCall).build()))
                .build();
        return LlmResponse.builder().content(content).turnComplete(true).build();
    }
}
