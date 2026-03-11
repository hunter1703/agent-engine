package com.agentengine.engine.context;

import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.api.beans.session.SessionInfo;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.repository.AgentSessionRepository;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compacts conversation history once it exceeds a token threshold, keeping a recent window
 * untouched and summarizing older content via an LLM call.
 *
 * <p>The summary is stored in session state (separate from raw events), so user-facing applications
 * retain the full event history while the LLM receives only compacted context.
 */
public final class CompactionContextManager implements ContextManager {
  private static final Logger LOG = LoggerFactory.getLogger(CompactionContextManager.class);
  private static final String SUMMARY_STATE_KEY_PREFIX = "context_summary_";
  private static final String DEFAULT_PROMPT_TEMPLATE =
      """
                  Summarize the following conversation history concisely, preserving all key facts, decisions, tool calls, and outcomes:

                  {context}
          """;

  private final int tokenThreshold;
  private final int recencyThreshold;
  private final String modelId;
  private final String promptTemplate;
  private final ModelProvider modelProvider;
  private final AgentSessionRepository sessionRepository;

  // In-memory cache — this instance is scoped to one (session, agent) pair via the runtime cache.
  private String cachedSummary;
  private boolean summaryLoaded;

  public CompactionContextManager(
      final int tokenThreshold,
      final int recencyThreshold,
      final String modelId,
      final String promptTemplate,
      final ModelProvider modelProvider,
      final AgentSessionRepository sessionRepository) {
    this.tokenThreshold = Math.max(1, tokenThreshold);
    this.recencyThreshold = Math.max(1, recencyThreshold);
    this.modelId = modelId;
    this.promptTemplate =
        StringUtils.isNotBlank(promptTemplate) ? promptTemplate : DEFAULT_PROMPT_TEMPLATE;
    this.modelProvider = modelProvider;
    this.sessionRepository = sessionRepository;
  }

  @Override
  public List<Content> buildPrompt(
      final String agentId, final String sessionId, final List<Content> contents) {
    if (CollectionUtils.isEmpty(contents)) {
      return List.of();
    }

    final String summaryKey = SUMMARY_STATE_KEY_PREFIX + agentId;
    final String existingSummary = loadSummary(sessionId, summaryKey);
    final int totalTokens = estimateTotalTokens(contents);

    if (totalTokens <= tokenThreshold) {
      return withSummaryPrefix(existingSummary, contents);
    }

    // Token threshold exceeded — compact older content, keep recent window as-is.
    final int splitIndex = findRecentSplitIndex(contents, recencyThreshold);
    final List<Content> older = contents.subList(0, splitIndex);
    final List<Content> recent = contents.subList(splitIndex, contents.size());

    final String summaryInput = buildSummaryInput(existingSummary, older);
    final String newSummary = callSummaryModel(summaryInput);

    if (newSummary != null) {
      persistSummary(sessionId, summaryKey, newSummary);
      return withSummaryPrefix(newSummary, recent);
    }

    LOG.warn(
        "Compaction failed for agent_id={} session_id={}; using full context.", agentId, sessionId);
    return withSummaryPrefix(existingSummary, contents);
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  /**
   * Returns the index at which the "recent" window starts, keeping the last {@code
   * recencyThreshold} tokens intact.
   */
  private static int findRecentSplitIndex(final List<Content> contents, final int recencyTokens) {
    int consumed = 0;
    for (int i = contents.size() - 1; i >= 0; i--) {
      final int tokens = estimateTokens(contents.get(i));
      if (consumed + tokens > recencyTokens) {
        return i + 1;
      }
      consumed += tokens;
    }
    return 0;
  }

  private static List<Content> withSummaryPrefix(
      final String summary, final List<Content> contents) {
    if (StringUtils.isBlank(summary)) {
      return new ArrayList<>(contents);
    }
    final List<Content> result = new ArrayList<>(contents.size() + 1);
    result.add(
        Content.builder()
            .role("user")
            .parts(List.of(Part.fromText("[Conversation Summary]\n" + summary)))
            .build());
    result.addAll(contents);
    return result;
  }

  private static String buildSummaryInput(final String existingSummary, final List<Content> older) {
    final StringBuilder sb = new StringBuilder();
    if (StringUtils.isNotBlank(existingSummary)) {
      sb.append("[Previous Summary]\n").append(existingSummary).append("\n\n");
    }
    sb.append("[Conversation]\n");
    for (final Content content : older) {
      sb.append(serialize(content)).append("\n");
    }
    return sb.toString().trim();
  }

  private static String serialize(final Content content) {
    if (content == null) {
      return "";
    }
    final StringBuilder sb = new StringBuilder(content.role().orElse("unknown")).append(": ");
    for (final Part part : content.parts().orElse(List.of())) {
      part.text().ifPresent(sb::append);
      part.functionCall()
          .ifPresent(
              fc ->
                  sb.append("[tool_call: ")
                      .append(fc.name().orElse("?"))
                      .append(fc.args())
                      .append("]"));
      part.functionResponse()
          .ifPresent(
              fr ->
                  sb.append("[tool_result: ")
                      .append(fr.name().orElse("?"))
                      .append(" → ")
                      .append(fr.response())
                      .append("]"));
    }
    return sb.toString();
  }

  private String callSummaryModel(final String input) {
    if (StringUtils.isBlank(modelId)) {
      LOG.warn("No summary model configured; skipping compaction.");
      return null;
    }
    final String prompt = promptTemplate.replace("{context}", input);
    final LlmRequest request =
        LlmRequest.builder()
            .contents(
                List.of(
                    Content.builder().role("user").parts(List.of(Part.fromText(prompt))).build()))
            .build();
    final BaseLlm model = modelProvider.get(modelId);
    try {
      final LlmResponse response = model.generateContent(request, false).blockingFirst();
      return response.content().map(Content::text).orElse(null);
    } catch (Exception ex) {
      LOG.warn("Summary model call failed.", ex);
      return null;
    } finally {
      modelProvider.release(modelId);
    }
  }

  // ── summary persistence ───────────────────────────────────────────────────

  private String loadSummary(final String sessionId, final String summaryKey) {
    if (summaryLoaded) {
      return cachedSummary;
    }
    try {
      cachedSummary =
          sessionRepository
              .findById(sessionId)
              .map(AgentSession::getSessionInfo)
              .map(SessionInfo::toSession)
              .map(s -> s.state().get(summaryKey))
              .map(Object::toString)
              .orElse(null);
    } catch (Exception ex) {
      LOG.warn("Failed to load summary for session_id={}", sessionId, ex);
    }
    summaryLoaded = true;
    return cachedSummary;
  }

  private void persistSummary(
      final String sessionId, final String summaryKey, final String summary) {
    cachedSummary = summary;
    try {
      sessionRepository.update(
          sessionId,
          Update.of(
              Operation.set(AgentSession.FIELD_SESSION_INFO + ".state." + summaryKey, summary),
              Operation.set(BaseEntity.FIELD_UPDATED_TIME, System.currentTimeMillis())));
    } catch (Exception ex) {
      LOG.warn("Failed to persist summary for session_id={}", sessionId, ex);
    }
  }

  // ── token estimation ──────────────────────────────────────────────────────

  private static int estimateTotalTokens(final List<Content> contents) {
    int total = 0;
    for (final Content content : contents) {
      total += estimateTokens(content);
    }
    return total;
  }

  private static int estimateTokens(final Content content) {
    if (content == null) {
      return 0;
    }
    final String text = content.text();
    if (StringUtils.isBlank(text)) {
      return 1;
    }
    return Math.max(1, text.trim().split("\\s+").length);
  }
}
