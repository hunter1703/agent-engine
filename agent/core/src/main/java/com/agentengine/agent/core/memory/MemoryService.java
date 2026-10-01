package com.agentengine.agent.core.memory;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.api.services.CommunityExpertsService;
import com.agentengine.agent.infra.session.SessionEventsRepository;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.agents.beans.config.DefaultModels;
import com.agentengine.util.agents.repository.DefaultModelsRepository;
import com.agentengine.util.common.RefCounted;
import com.agentengine.util.common.codec.JsonUtils;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.models.factories.EmbeddingUtils;
import com.agentengine.util.models.factories.Model;
import com.agentengine.util.models.factories.ModelProvider;
import com.agentengine.util.vectordb.VectorDbUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.adk.memory.BaseMemoryService;
import com.google.adk.memory.MemoryEntry;
import com.google.adk.memory.SearchMemoryResponse;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistent memory service backed by Qdrant.
 *
 * <p>Extracts and reconciles memories from completed sessions by running the community memory
 * agent, which enforces structured JSON output via the framework's {@code responseFormat}
 * validation and correction mechanism. Memories are stored as searchable vectors in Qdrant and
 * surfaced at query time via {@link #searchMemory}.
 */
@Singleton
public class MemoryService implements BaseMemoryService {

  private static final Logger LOG = LoggerFactory.getLogger(MemoryService.class);

  private static final int MAX_EXISTING_MEMORIES = 15;
  private static final int MAX_CONVERSATION_CHARS = 8000;
  private static final int EVENTS_PAGE_SIZE = 50;
  private static final String EMBEDDING_MODEL_ID_KEY = "embeddingModelId";
  private static final String TEXT_VECTOR =
      VectorDbUtils.vectorNames(Memory.class).get(Memory.FIELD_TEXT);

  private final CommunityExpertsService communityExpertsService;
  private final SessionEventsRepository sessionEventsRepository;
  private final MemoryRepository memoryRepository;
  private final DefaultModelsRepository defaultModelsRepository;
  private final ModelProvider modelProvider;

  public MemoryService(
      final CommunityExpertsService communityExpertsService,
      final SessionEventsRepository sessionEventsRepository,
      final MemoryRepository memoryRepository,
      final DefaultModelsRepository defaultModelsRepository,
      final ModelProvider modelProvider) {
    this.communityExpertsService = communityExpertsService;
    this.sessionEventsRepository = sessionEventsRepository;
    this.memoryRepository = memoryRepository;
    this.defaultModelsRepository = defaultModelsRepository;
    this.modelProvider = modelProvider;
  }

  /**
   * Extracts memories from the session's conversation. Runs as the session, for the user whose turn
   * it was — new memories belong to the session's agent, for that user.
   */
  @Override
  public Completable addSessionToMemory(final Session session) {
    return Completable.fromAction(
        () -> {
          final String conversation = buildConversationText(session.id());
          if (StringUtils.isBlank(conversation)) {
            return;
          }
          final List<Memory> existing = findExistingMemories(conversation);
          final MemoryDecisions decisions = invokeMemoryAgent(conversation, existing);
          applyDecisions(session.appName(), existing, decisions);
        });
  }

  /**
   * Recalls what the current session can reach — its agent's general memories, those about its
   * user, and its own — by the grants on them, whatever app and user ids the runner names.
   */
  @Override
  public Single<SearchMemoryResponse> searchMemory(
      final String appName, final String userId, final String query) {
    return Single.fromCallable(
        () -> {
          final List<MemoryEntry> entries =
              semanticMemorySearch(query, MAX_EXISTING_MEMORIES).stream()
                  .map(
                      memory ->
                          MemoryEntry.builder()
                              .content(Content.fromParts(Part.fromText(memory.getText())))
                              .build())
                  .toList();
          return SearchMemoryResponse.builder().memories(entries).build();
        });
  }

  private List<Memory> findExistingMemories(final String conversationText) {
    try {
      return semanticMemorySearch(conversationText, MAX_EXISTING_MEMORIES);
    } catch (final Exception e) {
      LOG.warn("Failed to retrieve existing memories", e);
      return List.of();
    }
  }

  private List<Memory> semanticMemorySearch(final String queryText, final int limit) {
    final String embeddingModelId = defaultModelsRepository.getEmbeddingModelId();
    if (StringUtils.isBlank(embeddingModelId)) {
      return List.of();
    }
    final Filter filter =
        Filters.semanticSearch(Memory.FIELD_TEXT, queryText)
            .withAdditional(Map.of(EMBEDDING_MODEL_ID_KEY, embeddingModelId));
    final Query query = new Query().withFilter(filter).withPage(new Page(0, limit));
    return CollectionUtils.nullSafeList(memoryRepository.findByQuery(query).getItems());
  }

  /**
   * Runs the memory agent for a single turn to produce structured ADD/UPDATE/DELETE/NOOP decisions.
   * The agent framework enforces the {@code responseFormat} schema and applies the correction loop
   * if the model output is invalid, so no manual validation is needed here.
   */
  private MemoryDecisions invokeMemoryAgent(
      final String conversation, final List<Memory> existing) {
    final String response =
        communityExpertsService.invokeExpert(
            CommunityExpertsService.MEMORY_AGENT,
            DefaultModels.CHAT_ID,
            UserMessage.ofText(buildPromptBody(conversation, existing)));
    return parseDecisions(response);
  }

  private void applyDecisions(
      final String agentId, final List<Memory> existing, final MemoryDecisions decisions) {
    final Map<String, Memory> existingById =
        CollectionUtils.transformToMap(existing, Memory::getId, Function.identity());
    final List<Memory> toAdd = new ArrayList<>();
    final List<Memory> toUpdate = new ArrayList<>();
    for (final MemoryDecision decision : decisions.decisions()) {
      try {
        switch (decision.operation()) {
          case "ADD" -> {
            if (StringUtils.isBlank(decision.text())) {
              continue;
            }
            final Memory memory = new Memory();
            memory.setId(UUID.randomUUID().toString());
            memory.setAgentId(agentId);
            memory.setText(decision.text());
            toAdd.add(memory);
          }
          case "UPDATE" -> {
            if (StringUtils.isBlank(decision.id()) || StringUtils.isBlank(decision.text())) {
              continue;
            }
            final Memory memory = existingById.get(decision.id());
            if (memory == null) {
              continue;
            }
            memory.setText(decision.text());
            toUpdate.add(memory);
          }
          case "DELETE" -> {
            if (StringUtils.isBlank(decision.id())) {
              continue;
            }
            final Memory memory = existingById.get(decision.id());
            if (memory != null && !memoryRepository.delete(memory)) {
              LOG.info("Memory {} changed or went since it was read; not deleted", memory.getId());
            }
          }
          case "NOOP" -> {}
          default -> LOG.warn("Unrecognised memory operation: {}", decision.operation());
        }
      } catch (final Exception e) {
        LOG.warn("Failed to apply memory decision: {}", decision, e);
      }
    }
    try {
      embedTexts(Stream.concat(toAdd.stream(), toUpdate.stream()).toList());
    } catch (final Exception e) {
      LOG.warn("Failed to embed {} memories", toAdd.size() + toUpdate.size(), e);
      return;
    }
    for (final Memory memory : toUpdate) {
      try {
        memoryRepository.save(memory);
      } catch (final Exception e) {
        LOG.warn("Failed to update memory {}", memory.getId(), e);
      }
    }
    if (!toAdd.isEmpty()) {
      try {
        memoryRepository.insertMany(toAdd);
      } catch (final Exception e) {
        LOG.warn("Failed to add {} memories", toAdd.size(), e);
      }
    }
  }

  /** Sets each memory's text vector, embedded with the customer's default embedding model. */
  private void embedTexts(final List<Memory> memories) {
    if (memories.isEmpty()) {
      return;
    }
    final String modelId = defaultModelsRepository.getEmbeddingModelId();
    if (StringUtils.isBlank(modelId)) {
      throw new IllegalStateException("No default embedding model configured");
    }
    try (RefCounted<Model.EmbeddingModel> refCounted = modelProvider.getEmbeddingModel(modelId)) {
      final List<float[]> vectors =
          EmbeddingUtils.embedAll(
              refCounted.value(), memories.stream().map(Memory::getText).toList());
      for (int i = 0; i < memories.size(); i++) {
        memories.get(i).setVector(TEXT_VECTOR, vectors.get(i));
      }
    }
  }

  private String buildConversationText(final String sessionId) {
    // Collect from the tail so recent turns are always included within the char budget, reading
    // the newest events a page at a time and stopping once the budget is reached.
    final List<String> lines = new ArrayList<>();
    int totalChars = 0;
    boolean trimmed = false;
    boolean lastPage = false;
    for (int offset = 0;
        totalChars < MAX_CONVERSATION_CHARS && !lastPage;
        offset += EVENTS_PAGE_SIZE) {
      final Page eventsPage = new Page(offset, EVENTS_PAGE_SIZE);
      final List<SessionEvent> page =
          sessionEventsRepository.getLatestCommittedEvents(sessionId, eventsPage).getItems();
      lastPage = page.size() < EVENTS_PAGE_SIZE;
      for (int i = 0; i < page.size(); i++) {
        final Content content = page.get(i).getContent();
        final String text = content == null ? null : content.text();
        if (StringUtils.isBlank(text)) {
          continue;
        }
        final String role =
            Objects.equals(Constants.AUTHOR_USER, page.get(i).getAuthor()) ? "USER" : "ASSISTANT";
        final String line = role + ": " + text + "\n";
        totalChars += line.length();
        lines.add(line);
        if (totalChars >= MAX_CONVERSATION_CHARS) {
          trimmed = i < page.size() - 1 || !lastPage;
          break;
        }
      }
    }
    // Reverse to restore chronological order before joining.
    Collections.reverse(lines);
    final String conversation = String.join("", lines).trim();
    if (trimmed) {
      return "[Note: this is a partial excerpt — earlier conversation history was omitted due to length. "
          + "Some existing memories may have been established in the omitted portion. "
          + "For each existing memory, ask: does the excerpt below give a concrete reason to change it "
          + "(new information, a correction, or a contradiction)? If yes, UPDATE or DELETE. "
          + "If the excerpt says nothing that bears on a memory, choose NOOP — "
          + "absence from this excerpt is not grounds for deletion.]\n\n"
          + conversation;
    }
    return conversation;
  }

  private static String buildPromptBody(final String conversation, final List<Memory> existing) {
    final StringBuilder sb = new StringBuilder();
    sb.append("EXISTING MEMORIES:\n");
    if (existing.isEmpty()) {
      sb.append("[]\n");
    } else {
      final List<Map<String, String>> minifiedMemory = new ArrayList<>();
      for (final Memory memory : existing) {
        final Map<String, String> entry = new LinkedHashMap<>();
        entry.put("id", memory.getId());
        entry.put("text", memory.getText());
        minifiedMemory.add(entry);
      }
      sb.append(JsonUtils.toJson(minifiedMemory)).append('\n');
    }
    sb.append("\nCONVERSATION:\n").append(conversation);
    return sb.toString();
  }

  private static MemoryDecisions parseDecisions(final String responseText) {
    if (StringUtils.isBlank(responseText)) {
      return MemoryDecisions.empty();
    }
    try {
      final MemoryDecisions parsed =
          JsonUtils.parseJsonPayload(responseText, new TypeReference<>() {});
      return new MemoryDecisions(
          CollectionUtils.nullSafeList(
              parsed == null || parsed.decisions() == null ? List.of() : parsed.decisions()));
    } catch (final Exception e) {
      LOG.warn("Failed to parse memory decisions from response: {}", responseText, e);
      return MemoryDecisions.empty();
    }
  }

  private record MemoryDecisions(List<MemoryDecision> decisions) {
    public static MemoryDecisions empty() {
      return new MemoryDecisions(List.of());
    }
  }

  private record MemoryDecision(String operation, String id, String text) {}
}
