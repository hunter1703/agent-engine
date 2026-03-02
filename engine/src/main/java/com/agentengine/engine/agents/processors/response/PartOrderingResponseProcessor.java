package com.agentengine.engine.agents.processors.response;

import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Enforces a canonical order of parts within an LLM response.
 *
 * <p>Canonical Order:
 * 1. Thoughts (thought == true)
 * 2. Regular Text (thought == false && text.isPresent())
 * 3. Tool Calls (functionCall.isPresent())
 * 4. Tool Responses (functionResponse.isPresent())
 * 5. Everything else
 *
 * <p>Ownership: Protocol-level part ordering and UI consistency.
 */
public final class PartOrderingResponseProcessor implements ResponseProcessor {
  public static final PartOrderingResponseProcessor INSTANCE = new PartOrderingResponseProcessor();

  private static final Comparator<Part> PART_COMPARATOR = (p1, p2) -> {
    int o1 = getOrder(p1);
    int o2 = getOrder(p2);
    return Integer.compare(o1, o2);
  };

  @Override
  public Single<ResponseProcessingResult> processResponse(
      final InvocationContext context, final LlmResponse response) {
    return response.content()
        .<Single<ResponseProcessingResult>>map(content -> {
          final List<Part> parts = content.parts().orElse(List.of());
          if (parts.size() <= 1) {
            return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
          }

          final List<Part> orderedParts = new ArrayList<>(parts);
          orderedParts.sort(PART_COMPARATOR);

          final LlmResponse orderedResponse = response.toBuilder()
              .content(content.toBuilder().parts(orderedParts).build())
              .build();

          return Single.just(ResponseProcessingResult.create(orderedResponse, List.of(), Optional.empty()));
        })
        .orElseGet(() -> Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty())));
  }

  private static int getOrder(final Part part) {
    if (part.thought().orElse(false)) {
      return 1;
    }
    if (part.text().isPresent()) {
      return 2;
    }
    if (part.functionCall().isPresent()) {
      return 3;
    }
    if (part.functionResponse().isPresent()) {
      return 4;
    }
    return 5;
  }
}
