package com.agentengine.engine.agents.processors.response;

import com.agentengine.engine.api.beans.config.GuardrailAction;
import com.agentengine.engine.api.beans.config.GuardrailErrorMode;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.guardrails.Guardrail;
import com.agentengine.engine.guardrails.GuardrailContext;
import com.agentengine.engine.guardrails.GuardrailDecision;
import com.agentengine.engine.guardrails.GuardrailUtils;
import com.agentengine.engine.utils.RunStateUtils;
import com.agentengine.engine.utils.Violation;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Applies output guardrails before response emission.
 *
 * <p>Behavior:
 * - WARN (retryable): strip visible text and continue the loop for regeneration.
 * - WARN (informational): keep current response and continue without forcing turn-complete.
 * - BLOCK/ESCALATE: replace output with safe policy text and terminate turn.
 */
public final class GuardrailResponseProcessor implements ResponseProcessor {
  private final List<Guardrail> guardrails;
  private final GuardrailErrorMode errorMode;

    public GuardrailResponseProcessor(List<Guardrail> guardrails, GuardrailErrorMode errorMode) {
        this.guardrails = guardrails;
        this.errorMode = errorMode;
    }

    @Override
  public Single<ResponseProcessingResult> processResponse(
      final InvocationContext context, final LlmResponse response) {
    if (response.partial().orElse(false) || response.content().isEmpty()) {
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }

    final Content content = response.content().orElse(null);
    if (content == null) {
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }

    final String text = content.text();
    if (StringUtils.isBlank(text)) {
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }

    final GuardrailContext guardrailContext = GuardrailContext.builder().invocationContext(context).text(text).build();
    final GuardrailDecision decision = GuardrailUtils.evaluate(guardrailContext, guardrails, errorMode);
    if (decision.action() == GuardrailAction.ALLOW) {
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }

    if (decision.action() == GuardrailAction.WARN) {
      RunStateUtils.getState(context)
          .addViolation(
              Violation.builder(decision.code())
                  .message(decision.message())
                  .correctionMessage(decision.message())
                  .details(decision.details())
                  .build());

      if (!requiresRegeneration(decision)) {
        return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
      }

      final LlmResponse updated =
          response.toBuilder()
              .content(stripPlainText(content))
              .turnComplete(false)
              .finishReason(Optional.empty())
              .build();
      return Single.just(ResponseProcessingResult.create(updated, List.of(), Optional.empty()));
    }

    final String blockMessage =
        StringUtils.isNotBlank(decision.message())
            ? decision.message()
            : "The response was blocked by guardrail policy.";
    final Content.Builder builder = Content.builder().parts(List.of(Part.fromText(blockMessage)));
    content.role().ifPresent(builder::role);
    final LlmResponse updated = response.toBuilder().content(builder.build()).turnComplete(true).build();
    return Single.just(ResponseProcessingResult.create(updated, List.of(), Optional.empty()));
  }

  private static boolean requiresRegeneration(final GuardrailDecision decision) {
    final Map<String, Object> details = decision.details();
    final Object explicit = details == null ? null : details.get("retry_required");
    if (explicit instanceof Boolean value) {
      return value;
    }
    return "off_topic_steer".equals(decision.code());
  }

  private static Optional<Content> stripPlainText(final Content content) {
    final List<Part> kept = new ArrayList<>();
    for (final Part part : content.parts().orElse(List.of())) {
      if (part.text().isEmpty() || part.thought().orElse(false)) {
        kept.add(part);
      }
    }
    if (kept.isEmpty()) {
      return Optional.empty();
    }
    final Content.Builder builder = Content.builder().parts(kept);
    content.role().ifPresent(builder::role);
    return Optional.of(builder.build());
  }
}
