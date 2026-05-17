package com.agentengine.agent.core.memory;

import com.agentengine.agent.api.services.CommunityRegistry;
import com.agentengine.agent.api.services.SessionHistoryService;
import com.agentengine.agent.infra.agents.Agent;
import com.agentengine.agent.infra.factories.agent.AgentProvider;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.mongodb.infra.DefaultModelsConfig;
import com.agentengine.util.mongodb.infra.InfraConfigService;
import com.google.adk.apps.App;
import com.google.adk.memory.BaseMemoryService;
import com.google.adk.memory.MemoryEntry;
import com.google.adk.memory.SearchMemoryResponse;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
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
    private static final String EMBEDDING_MODEL_ID_KEY = "embeddingModelId";

    private final CommunityRegistry communityRegistry;
    private final SessionHistoryService sessionHistoryService;
    private final AgentProvider agentProvider;
    private final MemoryStore memoryStore;
    private final InfraConfigService infraConfigService;

    public MemoryService(
            final CommunityRegistry communityRegistry,
            final SessionHistoryService sessionHistoryService,
            final AgentProvider agentProvider,
            final MemoryStore memoryStore,
            final InfraConfigService infraConfigService) {
        this.communityRegistry = communityRegistry;
        this.sessionHistoryService = sessionHistoryService;
        this.agentProvider = agentProvider;
        this.memoryStore = memoryStore;
        this.infraConfigService = infraConfigService;
    }

    @Override
    public Completable addSessionToMemory(final Session session) {
        return Completable.fromAction(() -> {
            final List<SessionEvent> events =
                    CollectionUtils.nullSafeList(sessionHistoryService.getSessionEvents(session.id()));
            final String conversation = buildConversationText(events);
            if (StringUtils.isBlank(conversation)) {
                return;
            }
            final String agentId = session.appName();
            final String userId = session.userId();
            final List<Memory> existing = findExistingMemories(agentId, userId, conversation);
            final List<MemoryDecision> decisions = invokeMemoryAgent(conversation, existing);
            applyDecisions(agentId, userId, decisions, existing);
        });
    }

    @Override
    public Single<SearchMemoryResponse> searchMemory(final String agentId, final String userId, final String query) {
        return Single.fromCallable(() -> {
            final List<Memory> results = semanticMemorySearch(agentId, userId, query, MAX_EXISTING_MEMORIES);
            final List<MemoryEntry> entries = results.stream()
                    .map(memory -> MemoryEntry.builder()
                            .content(Content.fromParts(Part.fromText(memory.getText())))
                            .build())
                    .toList();
            return SearchMemoryResponse.builder().setMemories(entries).build();
        });
    }

    private List<Memory> findExistingMemories(
            final String agentId, final String userId, final String conversationText) {
        try {
            return semanticMemorySearch(agentId, userId, conversationText, MAX_EXISTING_MEMORIES);
        } catch (final Exception e) {
            LOG.warn("Failed to retrieve existing memories for agent={} user={}", agentId, userId, e);
            return List.of();
        }
    }

    private List<Memory> semanticMemorySearch(
            final String agentId, final String userId, final String queryText, final int limit) {
        final String embeddingModelId = infraConfigService
                .<DefaultModelsConfig>findById(DefaultModelsConfig.CATEGORY, DefaultModelsConfig.CONFIG_ID)
                .getEmbeddingModelId();
        if (StringUtils.isBlank(embeddingModelId)) {
            return List.of();
        }
        final Filter filter = Filters.and(
                Filters.semanticSearch(Memory.FIELD_TEXT, queryText)
                        .withAdditional(Map.of(EMBEDDING_MODEL_ID_KEY, embeddingModelId)),
                Filters.eq(Memory.FIELD_AGENT_ID, agentId),
                Filters.eq(Memory.FIELD_USER_ID, userId));
        final Query query = new Query().withFilter(filter).withPage(new Page(0, limit));
        return CollectionUtils.nullSafeList(memoryStore.findByQuery(query).getItems());
    }

    /**
     * Runs the memory agent for a single turn to produce structured ADD/UPDATE/DELETE/NOOP
     * decisions. The agent framework enforces the {@code responseFormat} schema and applies the
     * correction loop if the model output is invalid, so no manual validation is needed here.
     */
    private List<MemoryDecision> invokeMemoryAgent(final String conversation, final List<Memory> existing) {
        final BaseAgentConfig config = communityRegistry.getExpert(CommunityRegistry.MEMORY_AGENT);
        if (config == null) {
            LOG.warn("Memory agent config not found in community registry; skipping memory extraction.");
            return List.of();
        }
        try {
            final Agent agent = agentProvider.create(config);
            final InMemorySessionService sessionService = new InMemorySessionService();
            final String sessionId = UUID.randomUUID().toString();
            sessionService
                    .createSession(config.getId(), AgentSession.DEFAULT_USER_ID, null, sessionId)
                    .blockingGet();
            final Runner runner = Runner.builder()
                    .app(App.builder().rootAgent(agent).name(config.getId()).build())
                    .sessionService(sessionService)
                    .build();
            final Content prompt = Content.fromParts(Part.fromText(buildPromptBody(conversation, existing)));
            final String responseText = runner.runAsync(AgentSession.DEFAULT_USER_ID, sessionId, prompt)
                    .filter(event -> event.turnComplete().orElse(false))
                    .map(event -> event.content().map(Content::text).orElse(null))
                    .filter(text -> !StringUtils.isBlank(text))
                    .firstElement()
                    .blockingGet();
            agent.close().blockingAwait();
            return parseDecisions(responseText);
        } catch (final Exception e) {
            LOG.warn("Memory agent invocation failed; skipping memory extraction.", e);
            return List.of();
        }
    }

    private void applyDecisions(
            final String agentId,
            final String userId,
            final List<MemoryDecision> decisions,
            final List<Memory> existing) {
        final Map<String, Memory> existingById =
                CollectionUtils.transformToMap(existing, Memory::getId, Function.identity());
        for (final MemoryDecision decision : decisions) {
            try {
                switch (decision.operation()) {
                    case "ADD" -> {
                        if (StringUtils.isBlank(decision.text())) {
                            continue;
                        }
                        final Memory memory = new Memory();
                        memory.setId(UUID.randomUUID().toString());
                        memory.setAgentId(agentId);
                        memory.setUserId(userId);
                        memory.setText(decision.text());
                        memoryStore.save(memory);
                    }
                    case "UPDATE" -> {
                        if (StringUtils.isBlank(decision.id()) || StringUtils.isBlank(decision.text())) {
                            continue;
                        }
                        final Memory toUpdate = existingById.get(decision.id());
                        if (toUpdate == null) {
                            continue;
                        }
                        toUpdate.setText(decision.text());
                        memoryStore.save(toUpdate);
                    }
                    case "DELETE" -> {
                        if (StringUtils.isBlank(decision.id())) {
                            continue;
                        }
                        memoryStore.deleteById(decision.id());
                    }
                    case "NOOP" -> {}
                    default -> LOG.warn("Unrecognised memory operation: {}", decision.operation());
                }
            } catch (final Exception e) {
                LOG.warn("Failed to apply memory decision: {}", decision, e);
            }
        }
    }

    private static String buildConversationText(final List<SessionEvent> events) {
        // Collect from the tail so recent turns are always included within the char budget.
        final List<String> lines = new ArrayList<>();
        int totalChars = 0;
        boolean trimmed = false;
        for (int i = events.size() - 1; i >= 0; i--) {
            final Content content = events.get(i).getContent();
            final String text = content == null ? null : content.text();
            if (StringUtils.isBlank(text)) {
                continue;
            }
            final String role =
                    Objects.equals(Constants.AUTHOR_USER, events.get(i).getAuthor()) ? "USER" : "ASSISTANT";
            final String line = role + ": " + text + "\n";
            totalChars += line.length();
            lines.add(line);
            if (totalChars >= MAX_CONVERSATION_CHARS) {
                trimmed = i > 0;
                break;
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
            sb.append("[\n");
            for (final Memory m : existing) {
                sb.append("  {\"id\": \"")
                        .append(m.getId())
                        .append("\", \"text\": \"")
                        .append(m.getText().replace("\"", "\\\""))
                        .append("\"}\n");
            }
            sb.append("]\n");
        }
        sb.append("\nCONVERSATION:\n").append(conversation);
        return sb.toString();
    }

    private static List<MemoryDecision> parseDecisions(final String responseText) {
        if (StringUtils.isBlank(responseText)) {
            return List.of();
        }
        try {
            final MemoryDecisions parsed = JsonUtils.fromJson(responseText, MemoryDecisions.class);
            return CollectionUtils.nullSafeList(
                    parsed == null || parsed.decisions() == null ? List.of() : parsed.decisions());
        } catch (final Exception e) {
            LOG.warn("Failed to parse memory decisions from response: {}", responseText, e);
            return List.of();
        }
    }

    private record MemoryDecisions(List<MemoryDecision> decisions) {}

    private record MemoryDecision(String operation, String id, String text) {}
}
