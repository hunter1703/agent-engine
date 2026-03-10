package com.agentengine.engine.model;

import com.agentengine.util.StringUtils;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.models.langchain4j.LangChain4j;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.List;

public final class NormalizedLangChain4j extends LangChain4j {

  public NormalizedLangChain4j(
      final ChatModel chatModel,
      final StreamingChatModel streamingChatModel,
      final String modelName) {
    super(chatModel, streamingChatModel, modelName);
  }

  @Override
  public Flowable<LlmResponse> generateContent(final LlmRequest llmRequest, final boolean stream) {
    final Flowable<LlmResponse> responses = super.generateContent(llmRequest, stream);
    return stream ? normalize(responses) : responses;
  }

  private static Flowable<LlmResponse> normalize(final Flowable<LlmResponse> responses) {
    return Flowable.defer(
        () -> {
          final ResponseState state = new ResponseState();
          return responses
              .concatMap(response -> mapResponse(response, state))
              .concatWith(Flowable.defer(() -> finalizeStream(state)));
        });
  }

  private static Flowable<LlmResponse> mapResponse(
      LlmResponse response, final ResponseState responseState) {
    response = ensureModelRole(response);
    if (response == null) {
      return Flowable.empty();
    }
    if (hasToolParts(response)) {
      Flowable<LlmResponse> result = Flowable.just(markFinalWithIds(response));
      if (!responseState.fullText.isEmpty()) {
        // If we have accumulated text, emit it as a partial response before the tool call
        final LlmResponse textResponse =
            markPartial(
                responseState.lastTextResponse,
                responseState.fullText.toString());
        result = Flowable.just(textResponse, markFinalWithIds(response));
        responseState.fullText.setLength(0);
        responseState.lastTextResponse = null;
      }
      return result;
    }
    // null if no content or non-text content part is present
    final String delta = extractTextDelta(response);
    // if current response has function calls, return as is i.e. consider it as
    // "complete" and no need to add partial information
    if (delta == null) {
      return Flowable.just(response);
    }
    responseState.fullText.append(delta);
    responseState.lastTextResponse = response;
    return Flowable.just(markPartial(response));
  }

  private static LlmResponse ensureModelRole(final LlmResponse response) {
    if (response == null) {
      return null;
    }
    final Content content = response.content().orElse(null);
    if (content == null) {
      return response;
    }
    if (content.role().filter("model"::equalsIgnoreCase).isPresent()) {
      return response;
    }
    // In the streaming path (LangChain4j), partial chunks are created with
    // Content.fromParts(...), which defaults the role to "user"
    final Content updatedContent = content.toBuilder().role("model").build();
    return response.toBuilder().content(updatedContent).build();
  }

  private static LlmResponse markPartial(final LlmResponse response) {
    return response.toBuilder().partial(true).turnComplete(false).build();
  }

  private static String extractTextDelta(final LlmResponse response) {
    final Content content = response.content().orElse(null);
    if (content == null) {
      return null;
    }
    final List<Part> parts = content.parts().orElse(List.of());
    if (parts.isEmpty()) {
      return null;
    }
    final StringBuilder builder = new StringBuilder();
    for (final Part part : parts) {
      if (part.functionCall().isPresent() || part.functionResponse().isPresent()) {
        return null;
      }
      final String text = part.text().orElse(null);
      if (StringUtils.isNotBlank(text)) {
        builder.append(text);
      }
    }
    final String combined = builder.toString();
    return StringUtils.isBlank(combined) ? null : combined;
  }

  private static Flowable<LlmResponse> finalizeStream(final ResponseState responseState) {
    if (responseState.fullText.isEmpty()) {
      return Flowable.empty();
    }
    return Flowable.just(
        markFinal(responseState.lastTextResponse, responseState.fullText.toString()));
  }

  private static LlmResponse markFinal(final LlmResponse response, final String fullText) {
    final Content content = Content.builder().role("model").parts(Part.fromText(fullText)).build();
    return response.toBuilder().content(content).partial(false).turnComplete(true).build();
  }

  private static LlmResponse markFinal(final LlmResponse response) {
    return response.toBuilder().partial(false).turnComplete(true).build();
  }

  private static LlmResponse markFinalWithIds(final LlmResponse response) {
    if (response == null) {
      return null;
    }
    final Content content = response.content().orElse(null);
    if (content == null) {
      return markFinal(response);
    }
    final List<Part> parts = content.parts().orElse(List.of());
    if (parts.isEmpty()) {
      return markFinal(response);
    }

    final List<Part> updatedParts = new ArrayList<>();
    boolean modified = false;

    for (final Part part : parts) {
      final com.google.genai.types.FunctionCall call = part.functionCall().orElse(null);
      if (call != null && call.id().isEmpty()) {
        final com.google.genai.types.FunctionCall updatedCall =
            call.toBuilder()
                .id(com.google.adk.flows.llmflows.Functions.generateClientFunctionCallId())
                .build();
        updatedParts.add(part.toBuilder().functionCall(updatedCall).build());
        modified = true;
      } else {
        updatedParts.add(part);
      }
    }

    if (!modified) {
      return markFinal(response);
    }

    final Content updatedContent = content.toBuilder().parts(updatedParts).build();
    return response.toBuilder()
        .content(updatedContent)
        .partial(false)
        .turnComplete(true)
        .build();
  }

  private static LlmResponse markPartial(final LlmResponse response, final String fullText) {
    final Content content = Content.builder().role("model").parts(Part.fromText(fullText)).build();
    return response.toBuilder().content(content).partial(true).turnComplete(false).build();
  }

  private static boolean hasToolParts(final LlmResponse response) {
    final Content content = response.content().orElse(null);
    if (content == null) {
      return false;
    }
    for (final Part part : content.parts().orElse(List.of())) {
      if (part.functionCall().isPresent() || part.functionResponse().isPresent()) {
        return true;
      }
    }
    return false;
  }

  private static class ResponseState {
    private LlmResponse lastTextResponse = null;
    private final StringBuilder fullText = new StringBuilder();
  }
}
