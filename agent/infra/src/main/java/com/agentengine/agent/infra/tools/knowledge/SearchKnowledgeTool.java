package com.agentengine.agent.infra.tools.knowledge;

import com.agentengine.agent.infra.annotations.DiscoverableTool;
import com.agentengine.agent.infra.annotations.ToolConstructor;
import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.catalog.api.services.AgentService;
import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.config.KnowledgeSettings;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.mongodb.infra.DefaultModelsConfig;
import com.agentengine.util.mongodb.infra.InfraConfigService;
import com.google.adk.tools.ToolContext;
import java.util.List;
import java.util.Map;

/**
 * Searches indexed knowledge chunks for content semantically similar to a natural-language query.
 *
 * <p>The search can be scoped to a specific knowledge item (via {@code knowledgeId}) or to all
 * knowledge indexed for the current agent. Results are ranked by vector similarity.
 */
@DiscoverableTool
public final class SearchKnowledgeTool extends Tool {

    /** Embedding-model additional key expected by {@code VectorStore.rewriteSemanticFilter}. */
    private static final String EMBEDDING_MODEL_ID_KEY = "embeddingModelId";

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "search_knowledge",
            "Searches indexed knowledge for content semantically relevant to the query. "
                    + "Scope the search to a specific document by providing its knowledgeId, "
                    + "or search all knowledge for the current agent when knowledgeId is omitted. "
                    + "Returns: { chunks: [...], total, offset, limit }.");

    private final KnowledgeService knowledgeService;
    private final AgentService agentService;
    private final InfraConfigService infraConfigService;

    @ToolConstructor
    public SearchKnowledgeTool(
            KnowledgeService knowledgeService, final AgentService agentService, final InfraConfigService infraConfigService) {
        super(DESCRIPTOR);
        this.knowledgeService = knowledgeService;
        this.agentService = agentService;
        this.infraConfigService = infraConfigService;
    }

    /**q
     * Executes a semantic search over indexed knowledge.
     *
     * @param query       natural-language search query
     * @param knowledgeId optional — limit search to a specific knowledge item
     * @param offset      pagination offset (default: 0)
     * @param limit       maximum results to return (default: 10)
     * @param toolContext provides agent identity for scope filtering
     */
    public ToolOutput<Map<String, Object>> execute(
            @ToolSchema(name = "query", description = "Natural-language search query") String query,
            @ToolSchema(
                            name = "knowledgeIds",
                            description = "Limit search to this knowledge items. Omit to search all agent knowledge.",
                            optional = true)
                    String knowledgeId,
            @ToolSchema(name = "offset", description = "Pagination offset (default: 0).", optional = true)
                    Integer offset,
            @ToolSchema(name = "limit", description = "Max results to return (default: 10).", optional = true)
                    Integer limit,
            ToolContext toolContext) {

        final int resolvedOffset = offset != null ? offset : 0;
        final int resolvedLimit = limit != null ? limit : 10;

        final Filter scopeFilter = StringUtils.isNotBlank(knowledgeId)
                ? Filters.eq(KnowledgeChunk.FIELD_KNOWLEDGE_ID, knowledgeId)
                : Filters.eq(
                        KnowledgeChunk.FIELD_AGENT_ID,
                        toolContext.invocationContext().agent().name());

        final Filter semanticFilter = Filters.semanticSearch("text", query)
                .withAdditional(Map.of(EMBEDDING_MODEL_ID_KEY, resolveModelId(toolContext.invocationContext().agent().name())));
        final Filter combined = Filters.and(semanticFilter, scopeFilter);

        final Query searchQuery = new Query().withFilter(combined).withPage(new Page(resolvedOffset, resolvedLimit));

        final PaginatedResult<KnowledgeChunk> result = knowledgeService.searchInKnowledge(searchQuery);
        final List<KnowledgeChunk> chunks = result.getItems();
        return ToolOutput.direct(Map.of(
                "chunks", chunks,
                "total", result.getTotal() != null ? result.getTotal() : chunks.size(),
                "offset", resolvedOffset,
                "limit", resolvedLimit));
    }

    private String resolveModelId(final String agentId) {
        final BaseAgentConfig agent = agentService.getAgent(agentId);
        final KnowledgeSettings settings = agent.getKnowledgeSettings();
        final String agentModelId = settings == null ? null : settings.getEmbeddingModelId();
        if (StringUtils.isNotBlank(agentModelId)) {
            return agentModelId;
        }
        final DefaultModelsConfig defaults =
                infraConfigService.findById(DefaultModelsConfig.CATEGORY, DefaultModelsConfig.CONFIG_ID);
        return defaults.getEmbeddingModelId();
    }
}
