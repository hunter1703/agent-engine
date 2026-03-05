package com.agentengine.engine.context;

import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.beans.session.SessionContextSummary;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SummarizingContextManager implements ContextManager {
  private static final Logger LOG = LoggerFactory.getLogger(SummarizingContextManager.class);

  private final int triggerTokens;
  private final int recencyTokens;
  private final String summaryModelId;
  private final BaseLlm summaryModel;
  private final SessionContextSummaryService summaryService;
  private final TokenEstimator tokenEstimator = TokenEstimator.INSTANCE;

  public SummarizingContextManager(
      final int triggerTokens,
      final int recencyTokens,
      final String summaryModelId,
      final BaseLlm summaryModel,
      final SessionContextSummaryService summaryService) {
    this.triggerTokens = triggerTokens;
    this.recencyTokens = recencyTokens;
    this.summaryModelId = summaryModelId;
    this.summaryModel = summaryModel;
    this.summaryService = summaryService;
  }

  @Override
  public List<Content> buildPrompt(final String agentId, final String sessionId, List<Content> contents) {
    contents = CollectionUtils.nullSafeList(contents);
    if (contents.isEmpty()) {
      return contents;
    }

    final Optional<SessionContextSummary> stored = summaryService.get(sessionId);
    final SummaryState summaryState = normalizeSummaryState(contents, stored.orElse(null));
    final List<Content> unsummarizedTail =
        new ArrayList<>(contents.subList(summaryState.cutoffContentCount(), contents.size()));
    final List<Content> effectivePrompt =
        buildEffectivePrompt(summaryState.summaryText(), unsummarizedTail);

    if (tokenEstimator.estimateTokens(effectivePrompt) <= triggerTokens) {
      return effectivePrompt;
    }

    final ContextWindow window = splitContext(unsummarizedTail);
    if (window.older().isEmpty()) {
      return effectivePrompt;
    }

    final int newCutoff = summaryState.cutoffContentCount() + window.older().size();
    final String transcript = buildTranscript(window.older());
    final String summaryText = summarize(summaryState.summaryText(), transcript);
    if (StringUtils.isBlank(summaryText)) {
      return effectivePrompt;
    }
    if (StringUtils.isNotBlank(sessionId)) {
      summaryService.upsert(
          sessionId,
          agentId,
          summaryText,
          newCutoff,
          tokenEstimator.estimateTokens(transcript),
          summaryModelId);
    }

    return buildEffectivePrompt(summaryText, window.recent());
  }

  private String summarize(final String existingSummary, final String transcript) {
    if (summaryModel == null) {
      return existingSummary;
    }
    final String prompt = buildSummaryPrompt(existingSummary, transcript);
    final LlmRequest request =
        LlmRequest.builder()
            .contents(List.of(Content.builder().role("user").parts(Part.fromText(prompt)).build()))
            .build();
    try {
      final LlmResponse response = summaryModel.generateContent(request, false).blockingFirst();
      final String text = response.content().map(Content::text).orElse("");
      if (StringUtils.isBlank(text)) {
        return existingSummary;
      }
      return text.trim();
    } catch (Exception ex) {
      LOG.warn("Context summarization failed; keeping previous summary.", ex);
      return existingSummary;
    }
  }

  private ContextWindow splitContext(final List<Content> contents) {
    final List<Content> recent = new ArrayList<>();
    int keptTokens = 0;
    int splitIndex = contents.size();
    for (int i = contents.size() - 1; i >= 0; i--) {
      final Content content = contents.get(i);
      final int contentTokens = tokenEstimator.estimateTokens(content);
      if (!recent.isEmpty() && keptTokens + contentTokens > recencyTokens) {
        break;
      }
      recent.add(content);
      keptTokens += contentTokens;
      splitIndex = i;
      if (keptTokens >= recencyTokens) {
        break;
      }
    }
    final List<Content> older = splitIndex == 0 ? List.of() : new ArrayList<>(contents.subList(0, splitIndex));
    return new ContextWindow(older, recent.reversed());
  }

  private static List<Content> buildEffectivePrompt(
      final String summaryText, final List<Content> tailContents) {
    if (StringUtils.isBlank(summaryText)) {
      return tailContents;
    }
    final List<Content> effectivePrompt = new ArrayList<>(tailContents.size() + 1);
    effectivePrompt.add(
        Content.builder().role("user").parts(Part.fromText("""
                Here is the summary of conversation :
                """ + summaryText.trim() + """
                Following is the non-summarized conversation :
                """
        )).build());
    effectivePrompt.addAll(tailContents);
    return effectivePrompt;
  }

  private static SummaryState normalizeSummaryState(final List<Content> contents, final SessionContextSummary summary) {
    if (summary == null || StringUtils.isBlank(summary.getSummaryText())) {
      return new SummaryState("", 0);
    }
    final int total = contents.size();
    final int storedCutoff = summary.getSummarizedUntilContentCount();
    if (storedCutoff <= 0) {
      return new SummaryState("", 0);
    }
    if (storedCutoff > total) {
      LOG.warn(
          "Ignoring stale session summary cutoff={} for context_size={}.",
          storedCutoff,
          total);
      return new SummaryState("", 0);
    }
    return new SummaryState(summary.getSummaryText().trim(), storedCutoff);
  }

  private static String buildTranscript(final List<Content> contents) {
    final StringBuilder builder = new StringBuilder();
    for (final Content content : contents) {
      final String text = content.text();
      if (StringUtils.isBlank(text)) {
        continue;
      }
      final String role = content.role().orElse("user");
      builder.append(role).append(": ").append(text.trim()).append('\n');
    }
    return builder.toString().trim();
  }

  private static String buildSummaryPrompt(final String existingSummary, final String transcript) {
    final String prior =
        StringUtils.isBlank(existingSummary) ? "(none)" : existingSummary.trim();
    return """
        Summarize the conversation context for future turns.
        Preserve facts, decisions, user preferences, constraints, and unresolved tasks.
        Keep it compact and actionable. Do not invent details.
        Return plain text only.

        Existing summary:
        %s

        Older conversation segment to compact:
        %s
        """
        .formatted(prior, transcript);
  }

  private record ContextWindow(List<Content> older, List<Content> recent) {}

  private record SummaryState(String summaryText, int cutoffContentCount) {}
}
