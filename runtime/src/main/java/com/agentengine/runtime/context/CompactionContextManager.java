package com.agentengine.runtime.context;

import static com.agentengine.engine.utils.ContentUtils.estimateTokens;

import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.runtime.factories.model.ModelProvider;
import com.agentengine.runtime.plugin.ContextManager;
import com.agentengine.runtime.repository.AgentSessionRepository;
import com.agentengine.runtime.utils.SessionUtils;
import com.agentengine.util.common.Cache;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.TemplateUtils;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import com.google.adk.models.BaseLlm;
import com.google.common.cache.CacheBuilder;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compacts conversation history once it exceeds a token threshold, keeping a
 * recent window untouched and summarizing older content via an LLM call.
 *
 * <p>
 * The summary is stored in session state (separate from raw events), so
 * user-facing applications retain the full event history while the LLM receives
 * only compacted context.
 */
public final class CompactionContextManager implements ContextManager {
  private static final Logger LOG = LoggerFactory.getLogger(CompactionContextManager.class);
  private static final String DEFAULT_PROMPT_TEMPLATE = """
              Summarize the following conversation history concisely, preserving all key facts, decisions, tool calls, and outcomes:

              {context}
      """;

  private final int tokenThreshold;
  private final int recencyThreshold;
  private final String modelId;
  private final String promptTemplate;
  private final ModelProvider modelProvider;
  private final AgentSessionRepository sessionRepository;
  private final Cache<String, String> summaryCache;

  public CompactionContextManager(final int tokenThreshold, int recencyThreshold, final String modelId, final String promptTemplate,
      final ModelProvider modelProvider, final AgentSessionRepository sessionRepository) {
    this.tokenThreshold = Math.max(1, tokenThreshold);
    recencyThreshold = Math.max(1, recencyThreshold);
    this.recencyThreshold = recencyThreshold > tokenThreshold ? (int) (tokenThreshold * 0.75) : recencyThreshold;
    this.modelId = modelId;
    this.promptTemplate = StringUtils.isNotBlank(promptTemplate) ? promptTemplate : DEFAULT_PROMPT_TEMPLATE;
    this.modelProvider = modelProvider;
    this.sessionRepository = sessionRepository;
    this.summaryCache = new Cache<>(CacheBuilder.newBuilder().maximumSize(1000), key -> {
      final String[] split = key.split(":");
      return loadSummary(split[0], split[1]);
    });
  }

  @Override
  public List<Content> buildPrompt(final String agentId, final String sessionId, final List<Content> contents) {
    if (CollectionUtils.isEmpty(contents)) {
      return List.of();
    }
    final String existingSummary = summaryCache.get(sessionId + ":" + agentId);
    final int totalTokens = estimateTotalTokens(contents) + StringUtils.estimateTextContent(existingSummary);

    if (totalTokens <= tokenThreshold) {
      return withSummaryPrefix(existingSummary, contents);
    }

    final int splitIndex = findRecentSplitIndex(contents, recencyThreshold);
    final List<Content> older = contents.subList(0, splitIndex);
    final List<Content> recent = contents.subList(splitIndex, contents.size());

    final String newSummary = callSummaryModel(buildSummaryInput(existingSummary, older));

    if (StringUtils.isNotBlank(newSummary)) {
      persistSummary(sessionId, agentId, newSummary);
      return withSummaryPrefix(newSummary, recent);
    }

    LOG.warn("Compaction failed for agent_id={} session_id={}; using full context.", agentId, sessionId);
    return withSummaryPrefix(existingSummary, contents);
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  /**
   * Returns the index at which the "recent" window starts, keeping the last
   * {@code
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

  private static List<Content> withSummaryPrefix(final String summary, final List<Content> contents) {
    if (StringUtils.isBlank(summary)) {
      return new ArrayList<>(contents);
    }
    final List<Content> result = new ArrayList<>(contents.size() + 1);
    result.add(Content.builder().role("user").parts(List.of(Part.fromText("[Conversation Summary]\n" + summary))).build());
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
      if (part.text().isPresent() && StringUtils.isNotBlank(part.text().get())) {
        sb.append(part.text().get()).append(" ");
      }
      part.functionCall().ifPresent(fc -> sb.append("[tool_call: ").append(fc.name().orElse("?")).append(fc.args()).append("]"));
      part.functionResponse()
          .ifPresent(fr -> sb.append("[tool_result: ").append(fr.name().orElse("?")).append(" → ").append(fr.response()).append("]"));
    }
    return sb.toString();
  }

  private String callSummaryModel(final String input) {
    if (StringUtils.isBlank(modelId)) {
      LOG.warn("No summary model configured; skipping compaction.");
      return null;
    }
    final String prompt = TemplateUtils.renderTextTemplate(promptTemplate, Map.of("context", input));
    final LlmRequest request = LlmRequest.builder()
        .contents(List.of(Content.builder().role("user").parts(List.of(Part.fromText(prompt))).build())).build();
    final BaseLlm model = modelProvider.acquire(modelId);
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

  private String loadSummary(final String sessionId, final String agentId) {
    return sessionRepository.findById(sessionId).map(AgentSession::getSessionInfo).map(SessionUtils::toSession).map(session -> {
      final Map<String, Object> summary = CollectionUtils.getMapFromMap(session.state(), "summary");
      return CollectionUtils.getStringValueFromMap(summary, agentId);
    }).map(Object::toString).orElse(null);
  }

  private void persistSummary(final String sessionId, final String agentId, final String summary) {
    try {
      sessionRepository.update(sessionId,
          Update.of(Operation.set(AgentSession.FIELD_SESSION_INFO + ".state." + summary + "." + agentId, summary),
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
}
