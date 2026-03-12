package com.agentengine.engine.model;

import com.agentengine.util.common.StringUtils;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.models.langchain4j.LangChain4j;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.reactivex.rxjava3.core.Flowable;
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
    return stream ? normalizeStreamingResponses(responses) : responses;
  }

  static Flowable<LlmResponse> normalizeStreamingResponses(final Flowable<LlmResponse> responses) {
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
      final LlmResponse toolResponse = markNonPartial(response);
      Flowable<LlmResponse> result = Flowable.just(toolResponse);
      if (!responseState.fullText.isEmpty()) {
        // Flush buffered text before surfacing the terminal tool-call chunk.
        final LlmResponse textResponse =
            markPartial(responseState.lastTextResponse, responseState.fullText.toString());
        result = Flowable.just(textResponse, toolResponse);
        responseState.fullText.setLength(0);
        responseState.lastTextResponse = null;
      }
      return result;
    }
    final String delta = extractTextDelta(response);
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
    return response.toBuilder().partial(true).build();
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
    return response.toBuilder().content(content).partial(false).build();
  }

  private static LlmResponse markNonPartial(final LlmResponse response) {
    return response.toBuilder().partial(false).build();
  }

  private static LlmResponse markPartial(final LlmResponse response, final String fullText) {
    final Content content = Content.builder().role("model").parts(Part.fromText(fullText)).build();
    return response.toBuilder().content(content).partial(true).build();
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
