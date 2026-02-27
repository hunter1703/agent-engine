package com.agentengine.engine.agents.processors.response;

import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.utils.RunState;
import com.agentengine.engine.utils.RunStateUtils;
import com.agentengine.engine.api.utils.TransitionResult;
import com.agentengine.engine.utils.Violation;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tracks and stitches thought text across streamed responses.
 *
 * <p>Responsibilities:
 * - Track thought text across partial chunks.
 * - Re-inject stored thoughts when the turn closes.
 * - Maintain RunState thought open/closed markers.
 *
 * <p>Ownership: thought lifecycle handling.
 */
public final class ThinkingResponseProcessor implements ResponseProcessor {
  public static final ThinkingResponseProcessor INSTANCE = new ThinkingResponseProcessor();

  @Override
  public Single<ResponseProcessingResult> processResponse(final InvocationContext context, final LlmResponse response) {
    final Content content = response.content().orElse(null);
    if (content == null) {
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }

    final List<Part> parts = content.parts().orElse(List.of());
    boolean hasThought = false;
    String lastThought = null;
    boolean hasToolCalls = false;
    boolean hasNonThoughtText = false;

    for (final Part part : parts) {
      if (part.functionCall().isPresent()) {
        hasToolCalls = true;
      }
      if (part.text().isPresent()) {
        final String text = part.text().orElse("");
        if (StringUtils.isNotBlank(text)) {
          if (part.thought().orElse(false)) {
            hasThought = true;
            lastThought = text;
          } else {
            hasNonThoughtText = true;
          }
        }
      }
    }

    final RunState runState = RunStateUtils.getState(context);
    if (hasThought) {
      final TransitionResult<Violation> transition = runState.transitionPhase(RunState.Phase.REASONING);
      if (transition.success()) {
        runState.updateLastThoughtText(lastThought);
        if (response.partial().orElse(false)) {
          runState.markThinkingOpen();
        } else {
          runState.markThinkingClosed();
        }
      } else {
        runState.markThinkingClosed();
      }
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }

    final boolean thinkingOpen = runState.thinkingOpen();
    lastThought = runState.lastThoughtText();
    final boolean shouldClose =
        thinkingOpen
            && StringUtils.isNotBlank(lastThought)
            && (hasToolCalls || hasNonThoughtText || response.turnComplete().orElse(false) || !response.partial().orElse(false));
    if (!shouldClose) {
      if (hasToolCalls || hasNonThoughtText) {
        if (!runState.transitionPhase(RunState.Phase.REASONING).success()) {
          return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
        }
      }
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }

    final List<Part> updatedParts = new ArrayList<>(parts.size() + 1);
    updatedParts.add(Part.builder().text(lastThought).thought(true).build());
    updatedParts.addAll(parts);

    final LlmResponse updatedResponse = response.toBuilder()
        .content(content.toBuilder().parts(updatedParts).build())
        .partial(false)
        .build();
    runState.markThinkingClosed();
    if (hasToolCalls || hasNonThoughtText) {
      if (!runState.transitionPhase(RunState.Phase.REASONING).success()) {
        return Single.just(ResponseProcessingResult.create(updatedResponse, List.of(), Optional.empty()));
      }
    }
    return Single.just(ResponseProcessingResult.create(updatedResponse, List.of(), Optional.empty()));
  }
}
