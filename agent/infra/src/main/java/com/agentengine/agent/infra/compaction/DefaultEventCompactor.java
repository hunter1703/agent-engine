package com.agentengine.agent.infra.compaction;

import com.agentengine.agent.infra.utils.EventUtils;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.agents.beans.config.CompactionContextStrategyConfig;
import com.agentengine.util.agents.beans.config.CompactionContextStrategyConfig.ThresholdUnit;
import com.agentengine.util.models.factories.ModelProvider;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.events.EventCompaction;
import com.google.adk.models.LlmRequest;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import io.reactivex.rxjava3.core.Maybe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Compacts the history once the context grows past a trigger size: the latest events, up to a
 * recency size, are kept as they are and every older event not yet summarized is summarized. An
 * event is summarized at most once; a summary is never summarized again, so the summaries stay in
 * the context beside the events no summary covers.
 *
 * <p>A tool call is never summarized without its result: the summary reaches on to the result when
 * it lies in the latest events. A call still waiting for its result is kept, with everything after
 * it, even beyond the recency size, so the result lands next to its call when it arrives.
 */
public final class DefaultEventCompactor implements EventCompactor {
  private static final int CHARACTERS_PER_TOKEN = 4;

  private final LLMSummarizer summarizer;
  private final ModelProvider modelProvider;
  private final String modelId;
  private final CompactionContextStrategyConfig config;

  /**
   * @param modelId the model whose context length the thresholds are measured against; it may be a
   *     default model sentinel
   */
  public DefaultEventCompactor(
      final LLMSummarizer summarizer,
      final ModelProvider modelProvider,
      final String modelId,
      final CompactionContextStrategyConfig config) {
    validate(config);
    this.summarizer = summarizer;
    this.modelProvider = modelProvider;
    this.modelId = modelId;
    this.config = config;
  }

  @Override
  public Maybe<Event> compact(final InvocationContext context, final LlmRequest request) {
    final LlmRequest lastRequest = SessionUtils.getSessionState(context).runState().lastRequest();
    if (lastRequest == null) {
      return Maybe.empty();
    }
    return Maybe.fromCallable(() -> eventsToSummarize(List.copyOf(context.session().events())))
        .flatMap(
            candidates ->
                summarizer.summarize(lastRequest).map(summary -> compactionEvent(candidates, summary)));
  }

  /** The compaction event for {@code events}, carrying the usage of the call that summarized them. */
  private static Event compactionEvent(final List<Event> events, final Event summary) {
    final Event.Builder builder =
        Event.builder()
            .author("user")
            .actions(
                EventActions.builder()
                    .compaction(
                        EventCompaction.builder()
                            .startTimestamp(events.getFirst().timestamp())
                            .endTimestamp(events.getLast().timestamp())
                            .compactedContent(summary.content().orElseThrow())
                            .build())
                    .build())
            .invocationId(Event.generateEventId());
    summary.usageMetadata().ifPresent(builder::usageMetadata);
    return builder.build();
  }

  /** The events to summarize now; null when the context is within the trigger size. */
  private List<Event> eventsToSummarize(final List<Event> events) {
    final OptionalInt maxContextLength = modelProvider.getMaxContextLength(modelId);
    if (maxContextLength.isEmpty()) {
      return null;
    }
    final int trigger = tokens(config.getTriggerThreshold(), maxContextLength.getAsInt());
    final int recency = tokens(config.getRecencyThreshold(), maxContextLength.getAsInt());
    final List<Event> uncoveredEvents = uncoveredEvents(events);
    if (contextTokens(events, uncoveredEvents) <= trigger) {
      return null;
    }
    final int lastCandidate = lastCandidate(uncoveredEvents, recency);
    return lastCandidate < 0 ? null : uncoveredEvents.subList(0, lastCandidate + 1);
  }

  /**
   * The events no summary covers: those after the last one a compaction covered, since every
   * compaction covers the events from the first uncovered one on. A compaction event comes after
   * the events kept out of it, so what it covers is told by timestamp, not by position.
   */
  private static List<Event> uncoveredEvents(final List<Event> events) {
    final long coveredUntil =
        events.stream()
            .filter(EventUtils::isCompactionEvent)
            .mapToLong(event -> event.actions().compaction().orElseThrow().endTimestamp())
            .max()
            .orElse(Long.MIN_VALUE);
    return events.stream()
        .filter(event -> !EventUtils.isCompactionEvent(event))
        .filter(event -> event.timestamp() > coveredUntil)
        .toList();
  }

  /**
   * The size of the context the next request carries: the prompt of the latest model response, that
   * response and what came after it. A compaction since that response changed what the prompt
   * holds, so the summaries and the events no summary covers are estimated instead.
   */
  private static int contextTokens(
      final List<Event> events, final List<Event> uncoveredEvents) {
    for (int index = events.size() - 1; index >= 0; index--) {
      final Event event = events.get(index);
      if (EventUtils.isCompactionEvent(event)) {
        break;
      }
      final GenerateContentResponseUsageMetadata usage = event.usageMetadata().orElse(null);
      if (usage != null && usage.promptTokenCount().isPresent()) {
        int tokens =
            usage.promptTokenCount().get()
                + usage.candidatesTokenCount().orElseGet(() -> estimatedTokens(event));
        for (final Event later : events.subList(index + 1, events.size())) {
          tokens += estimatedTokens(later);
        }
        return tokens;
      }
    }
    final int summaryTokens =
        events.stream()
            .filter(EventUtils::isCompactionEvent)
            .mapToInt(event -> estimatedTokens(event.actions().compaction().orElseThrow()))
            .sum();
    return summaryTokens
        + uncoveredEvents.stream().mapToInt(DefaultEventCompactor::estimatedTokens).sum();
  }

  /**
   * The index of the last event to summarize: the events before the latest ones that fit the
   * recency size, moved to the nearest place that does not split a tool call from its result, and
   * always leaving the newest event as it is; -1 when there is none.
   */
  private static int lastCandidate(final List<Event> history, final int recencyTokens) {
    int firstKeptIndex = history.size();
    int keptTokens = 0;
    while (firstKeptIndex > 0
        && keptTokens + estimatedTokens(history.get(firstKeptIndex - 1)) <= recencyTokens) {
      firstKeptIndex--;
      keptTokens += estimatedTokens(history.get(firstKeptIndex));
    }
    final int boundary = firstKeptIndex - 1;
    if (boundary < 0) {
      return -1;
    }
    final int[] resultReach = resultReach(history);
    for (int index = boundary; index <= history.size() - 2; index++) {
      if (resultReach[index] <= index) {
        return index;
      }
    }
    for (int index = boundary - 1; index >= 0; index--) {
      if (resultReach[index] <= index) {
        return index;
      }
    }
    return -1;
  }

  /**
   * For each event, the index of the latest result among the calls up to it; a call without a
   * result counts as past the end. Summarizing up to an event whose reach is no further than itself
   * leaves no call without its result.
   */
  private static int[] resultReach(final List<Event> history) {
    final Map<String, Integer> callIdVsResultIndex = new HashMap<>();
    for (int index = 0; index < history.size(); index++) {
      final int resultIndex = index;
      for (final FunctionResponse response : history.get(index).functionResponses()) {
        response.id().ifPresent(id -> callIdVsResultIndex.put(id, resultIndex));
      }
    }
    final int[] reach = new int[history.size()];
    int latest = -1;
    for (int index = 0; index < history.size(); index++) {
      for (final FunctionCall call : history.get(index).functionCalls()) {
        latest =
            Math.max(
                latest,
                call.id().map(id -> callIdVsResultIndex.getOrDefault(id, Integer.MAX_VALUE)).orElse(-1));
      }
      reach[index] = latest;
    }
    return reach;
  }

  private static int estimatedTokens(final Event event) {
    return event.stringifyContent().length() / CHARACTERS_PER_TOKEN;
  }

  private static int estimatedTokens(final EventCompaction compaction) {
    final String text = compaction.compactedContent().text();
    return text == null ? 0 : text.length() / CHARACTERS_PER_TOKEN;
  }

  private int tokens(final double threshold, final int maxContextLength) {
    return config.thresholdUnitEnum() == ThresholdUnit.PERCENT
        ? (int) (maxContextLength * threshold / 100)
        : (int) threshold;
  }

  private static void validate(final CompactionContextStrategyConfig config) {
    final ThresholdUnit unit = config.thresholdUnitEnum();
    if (unit == ThresholdUnit.UNKNOWN) {
      throw new IllegalArgumentException(
          "Unsupported compaction threshold unit: " + config.getThresholdUnit());
    }
    final double trigger = config.getTriggerThreshold();
    final double recency = config.getRecencyThreshold();
    if (recency <= 0 || recency >= trigger || (unit == ThresholdUnit.PERCENT && trigger > 100)) {
      throw new IllegalArgumentException(
          "Compaction thresholds must satisfy 0 < recency < trigger"
              + (unit == ThresholdUnit.PERCENT ? " <= 100" : "")
              + ": recency="
              + recency
              + ", trigger="
              + trigger);
    }
  }
}
