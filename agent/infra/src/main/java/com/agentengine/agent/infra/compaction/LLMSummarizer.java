package com.agentengine.agent.infra.compaction;

import com.agentengine.agent.infra.utils.ResponseUtils;
import com.agentengine.util.agents.ContentUtils;
import com.agentengine.util.common.RefCounted;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.models.factories.ModelProvider;
import com.google.adk.events.Event;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Maybe;
import java.util.ArrayList;
import java.util.List;

/**
 * Summarizes the messages of a request by asking a model: it is sent the request with one more
 * message, the instruction to summarize. When the model is the agent's own, the request keeps the
 * agent's system instruction and tools, so a provider that caches the start of a prompt has all of it
 * cached. Another model is sent the messages and the instruction alone.
 *
 * <p>The summary is a user message: a summary of the conversation is not something the model said,
 * and a user message keeps the history acceptable to providers that require one before any model
 * message.
 */
public final class LLMSummarizer {
  private static final String DEFAULT_INSTRUCTION =
      """
      Summarize the conversation above, focusing on key information and decisions made, as well as \
      any unresolved questions or tasks. Be concise and capture the essence of the interaction. \
      Earlier summaries in it are kept as they are: cover only the messages after them and do not \
      restate the summaries. Messages in <system_notice> tags are the runtime's reminders and \
      notices: leave them out. Do not call tools; answer with the summary alone.""";
  private static final String RETRY_REMINDER =
      """
       Your previous answer was not a plain-text summary. Reply with the summary as text only: no \
      tool calls and no other output.""";
  private static final int MAX_ATTEMPTS = 3;

  private final ModelProvider modelProvider;
  private final String agentModelId;
  private final String modelId;

    /**
   * @param agentModelId the model of the agent whose history is summarized
   * @param modelId the model that summarizes; it may be a default model sentinel
   * @param promptTemplate the instruction, where {@code {conversation_history}} stands for the
   *     conversation above; the default instruction when blank
   */
  public LLMSummarizer(
      final ModelProvider modelProvider,
      final String agentModelId,
      final String modelId,
      final String promptTemplate) {
    this.modelProvider = modelProvider;
    this.agentModelId = agentModelId;
    this.modelId = modelId;
  }

  /**
   * The summary of the messages of {@code request}, the request of the agent's last model call, as
   * an event whose content is the summary and whose usage metadata is what the summarizing call
   * used, cached tokens included. An answer that is not plain text, such as a tool call or no text,
   * is asked for again with a reminder, up to {@value #MAX_ATTEMPTS} times in all; empty when none
   * of them is a summary.
   */
  public Maybe<Event> summarize(final LlmRequest request) {
    return Maybe.using(
        () -> modelProvider.get(modelId),
        model -> summarize(model.value().model(), request, 1),
        RefCounted::close);
  }

  private Maybe<Event> summarize(final BaseLlm model, final LlmRequest request, final int attempt) {
    return model
        .generateContent(summaryRequest(request, attempt), false)
        .firstElement()
        .filter(LLMSummarizer::isSummary)
        .map(this::summaryEvent)
        .switchIfEmpty(
            Maybe.defer(
                () -> attempt < MAX_ATTEMPTS ? summarize(model, request, attempt + 1) : Maybe.empty()));
  }

  private static boolean isSummary(final LlmResponse response) {
    return ResponseUtils.isFinalAnswer(response)
        && response.content().map(content -> StringUtils.isNotBlank(content.text())).orElse(false);
  }

  private Event summaryEvent(final LlmResponse response) {
    final Event.Builder builder =
        Event.builder()
            .author("user")
            .content(
                ContentUtils.userMessage(
                    withSummaryTags(response.content().orElseThrow().parts().orElse(List.of()))));
    response.usageMetadata().ifPresent(builder::usageMetadata);
    return builder.build();
  }

  private LlmRequest summaryRequest(final LlmRequest request, final int attempt) {
    final List<Content> contents = new ArrayList<>(request.contents());
    contents.add(
        ContentUtils.userMessage(
            List.of(Part.fromText(attempt == 1 ? DEFAULT_INSTRUCTION : DEFAULT_INSTRUCTION + RETRY_REMINDER))));
    if (!ownsRequest()) {
      return LlmRequest.builder().model(modelId).contents(contents).build();
    }
    final GenerateContentConfig config =
        request.config().orElseGet(() -> GenerateContentConfig.builder().build());
    return request.toBuilder()
        .contents(contents)
        .config(
            config.toBuilder()
                .clearResponseMimeType()
                .clearResponseSchema()
                .clearResponseJsonSchema()
                .build())
        .build();
  }

  /** Whether the summarizing model is the agent's own, so its request can be the agent's. */
  private boolean ownsRequest() {
    return modelProvider.resolveId(agentModelId).equals(modelProvider.resolveId(modelId));
  }

  private static List<Part> withSummaryTags(final List<Part> summary) {
    final List<Part> parts = new ArrayList<>();
    parts.add(Part.fromText("<earlier_conversation_summary>"));
    // A reasoning model's thinking is not part of the summary.
    parts.addAll(summary.stream().filter(part -> !part.thought().orElse(false)).toList());
    parts.add(Part.fromText("</earlier_conversation_summary>"));
    parts.add(
        Part.fromText(
            """
            Note: Earlier conversation turns have been compacted into the summary above. If you need detailed context, intermediate drafts, or past calculations in your notebooks, read the notes in your notebooks to find the lay of the land."""
                .trim()));
    return parts;
  }
}
