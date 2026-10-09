package com.agentengine.agent.infra.compaction;

import com.agentengine.util.agents.ContentUtils;
import com.agentengine.agent.infra.utils.EventUtils;
import com.agentengine.util.common.RefCounted;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.models.factories.ModelProvider;
import com.google.adk.events.Event;
import com.google.adk.events.EventCompaction;
import com.google.adk.models.BaseLlm;
import com.google.adk.summarizer.BaseEventSummarizer;
import com.google.adk.summarizer.LlmEventSummarizer;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Maybe;
import java.util.ArrayList;
import java.util.List;

/**
 * Summarizes events with a model, through ADK's {@link LlmEventSummarizer}, and stores the summary
 * as a user message: a summary of the conversation is not something the model said, and a user
 * message keeps the history acceptable to providers that require one before any model message.
 */
public final class LLMSummarizer implements BaseEventSummarizer {

  private final ModelProvider modelProvider;
  private final String modelId;
  private final String promptTemplate;

  public LLMSummarizer(
      final ModelProvider modelProvider, final String modelId, final String promptTemplate) {
    this.modelProvider = modelProvider;
    this.modelId = modelId;
    this.promptTemplate = promptTemplate;
  }

  @Override
  public Maybe<Event> summarizeEvents(final List<Event> events) {
    return Maybe.using(
            () -> modelProvider.get(modelId),
            model -> summarizer(model.value().model()).summarizeEvents(events),
            RefCounted::close)
        .map(LLMSummarizer::withSummaryAsUserMessage);
  }

  private LlmEventSummarizer summarizer(final BaseLlm model) {
    return StringUtils.isBlank(promptTemplate)
        ? new LlmEventSummarizer(model)
        : new LlmEventSummarizer(model, promptTemplate);
  }

  private static Event withSummaryAsUserMessage(final Event event) {
    final EventCompaction compaction = event.actions().compaction().orElseThrow();
    final List<Part> parts = new ArrayList<>();
    parts.add(Part.fromText("<earlier_conversation_summary>"));
    // A reasoning model's thinking is not part of the summary.
    parts.addAll(
        compaction.compactedContent().parts().orElse(List.of()).stream()
            .filter(part -> !part.thought().orElse(false))
            .toList());
    parts.add(Part.fromText("</earlier_conversation_summary>"));
    return EventUtils.withCompactedContent(event, ContentUtils.userMessage(parts));
  }
}
