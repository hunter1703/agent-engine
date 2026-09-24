package com.agentengine.agent.infra.tools.knowledge;

import com.agentengine.agent.api.annotations.ToolArg;
import com.agentengine.agent.infra.agents.Agent;
import com.agentengine.agent.infra.annotations.ToolConstructor;
import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.config.KnowledgeSettings;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.repository.DefaultModelsRepository;
import com.agentengine.util.common.RefCounted;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.models.factories.Model;
import com.agentengine.util.models.factories.ModelProvider;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Searches indexed knowledge chunks for content semantically similar to a natural-language query.
 *
 * <p>The search can be scoped to a specific knowledge item (via {@code knowledgeId}) or to all
 * knowledge indexed for the current agent. Results are ranked by vector similarity.
 */
public final class SearchKnowledgeTool extends Tool {

  private static final Logger LOG = LoggerFactory.getLogger(SearchKnowledgeTool.class);

  /** Embedding-model additional key expected by {@code VectorStore.rewriteSemanticFilter}. */
  private static final String EMBEDDING_MODEL_ID_KEY = "embeddingModelId";

  private static final long HYPOTHETICAL_PASSAGE_TIMEOUT_SECONDS = 8L;

  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.SEARCH_KNOWLEDGE,
          """
                  Semantic search over the agent's indexed knowledge. The query is matched against
                  document text, so write it as a CONTENT phrase, not as an instruction to this tool.

                  HOW TO WRITE THE QUERY
                  - Shape: a short, standalone phrase (~5-15 words) naming the topic, entities, and
                    aspects you want to match. Prefer noun phrases over full questions.
                        good: "refund policy eligibility window"
                        bad:  "can you please find the refund policy?"
                  - Drop meta-language directed at this tool or at the act of retrieval itself:
                    "summarize", "find", "tell me", "please", "in the document", "from the
                    knowledge base" — and their noun forms, like "summary" or "overview" as a stand-
                    in for the topic. A query is a guess at words the matching passage itself would
                    contain; a phrase that names the KIND of information you want, rather than the
                    information itself, rarely appears verbatim in any source and matches poorly.
                  - Drop pronouns: replace "it", "this", "the file" with the actual nouns.
                  - Avoid negation ("not expensive") - it matches poorly. Query the positive concept
                    instead ("low-cost budget option").
                  - Add recall terms: synonyms, domain jargon, product/person names, dates, section
                    hints. More relevant terms => more relevant passages matched.

                  IF YOU DON'T KNOW THE SOURCE'S FORM YET
                  You won't always know upfront whether a source reads as prose, dialogue,
                  transcript, tabular data, legal clauses, etc. Don't guess — query the topic itself
                  (not a label for what you want from it) and treat the first call as exploratory.
                  The chunks that come back show you the source's actual phrasing and register;
                  reuse that vocabulary and style in follow-up queries rather than repeating a query
                  shape that didn't match well.

                  ONE IDEA PER CALL
                  Each call retrieves chunks for one direction. For broad, comparative, or multi-part
                  questions, issue MULTIPLE calls with different queries and merge the results,
                  instead of cramming everything into one long query.
                      "Is X better than Y?"  ->  call 1: "X strengths weaknesses trade-offs"
                                                 call 2: "Y strengths weaknesses trade-offs"

                  SCOPING
                  Pass knowledgeId when the user names a specific source; omit it to search all
                  knowledge indexed for this agent.

                  PAGINATION
                  Returns { chunks, offset, limit, hasMore }. When hasMore is true, call again with
                  a larger offset to page through further matches.

                  NOT FOR
                  Raw knowledge sources that have no indexed chunks. Read those whole with
                  READ_KNOWLEDGE_SOURCE instead.
                  """);

  private final KnowledgeService knowledgeService;
  private final DefaultModelsRepository defaultModelsRepository;
  private final ModelProvider modelProvider;

  @ToolConstructor
  public SearchKnowledgeTool(
      final KnowledgeService knowledgeService,
      final DefaultModelsRepository defaultModelsRepository,
      final ModelProvider modelProvider) {
    super(DESCRIPTOR);
    this.knowledgeService = knowledgeService;
    this.defaultModelsRepository = defaultModelsRepository;
    this.modelProvider = modelProvider;
  }

  /**
   * Executes a semantic search over indexed knowledge.
   *
   * @param query natural-language search query
   * @param knowledgeId optional — limit search to a specific knowledge item
   * @param offset pagination offset (default: 0)
   * @param limit maximum results to return (default: 10)
   * @param toolContext provides agent identity for scope filtering
   */
  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(
              name = "query",
              description =
                  "Content-shaped search phrase, NOT an instruction to this tool. The phrase is "
                      + "matched against document text, so write it the way the content itself "
                      + "would be phrased. "
                      + "Rules: (1) ~5-15 words, one topic; "
                      + "(2) prefer noun phrases over questions - "
                      + "'refund policy eligibility window', not 'what is the refund policy?'; "
                      + "(3) no meta-words directed at this tool or the retrieval act "
                      + "('summarize', 'find', 'please', 'in the document') - including their noun "
                      + "forms used as a stand-in for the topic ('summary', 'overview'), since a "
                      + "name for the KIND of information you want rarely appears verbatim in any "
                      + "source; no pronouns ('it', 'this file'), no negations; "
                      + "(4) include synonyms, domain terms, names and dates to surface more "
                      + "relevant passages; "
                      + "(5) don't know the source's form yet (prose, dialogue, tabular, etc.)? "
                      + "Query the topic itself and treat the first call as exploratory - the "
                      + "chunks that come back show you the source's real phrasing, so reuse it in "
                      + "follow-up queries. "
                      + "For multi-part questions, call this tool several times with different "
                      + "queries rather than once with a long one.")
          String query,
      @ToolArg(
              name = Constants.ToolArgs.KNOWLEDGE_ID,
              description =
                  "Restrict the search to a single indexed knowledge source. "
                      + "Provide this ONLY when the user names a specific source and a "
                      + "valid id for it is available in the conversation (for example from "
                      + "a prior tool call that lists the agent's knowledge). "
                      + "Do NOT invent or guess an id - an unknown id returns no results and "
                      + "looks like 'nothing found'. "
                      + "Omit to search across all knowledge indexed for this agent. "
                      + "Scoping narrows recall; it does not replace the query, which is still "
                      + "required.",
              optional = true)
          String knowledgeId,
      @ToolArg(
              name = "offset",
              description =
                  "Pagination offset. Leave unset for the first page; when a previous result had "
                      + "hasMore=true, pass that call's offset + limit to fetch the next page.",
              optional = true)
          Integer offset,
      @ToolArg(
              name = "limit",
              description =
                  "Max chunks to return (default 5). Increase for broad/comparative queries that "
                      + "need coverage across many chunks; keep small for targeted factual lookups.",
              optional = true)
          Integer limit,
      ToolContext toolContext) {

    final int resolvedOffset = offset != null ? offset : 0;
    final int resolvedLimit = limit != null ? limit : 5;

    final Filter scopeFilter =
        StringUtils.isNotBlank(knowledgeId)
            ? Filters.eq(KnowledgeChunk.FIELD_KNOWLEDGE_ID, knowledgeId)
            : Filters.eq(
                KnowledgeChunk.FIELD_AGENT_ID, toolContext.invocationContext().agent().name());

    final String embeddingText = hypotheticalPassage(query, knowledgeId);
    final Filter semanticFilter =
        Filters.semanticSearch("text", embeddingText)
            .withAdditional(Map.of(EMBEDDING_MODEL_ID_KEY, resolveModelId(toolContext)));
    final Filter combined = Filters.and(semanticFilter, scopeFilter);

    final Query searchQuery =
        new Query().withFilter(combined).withPage(new Page(resolvedOffset, resolvedLimit));

    final PaginatedResult<KnowledgeChunk> result = knowledgeService.searchInKnowledge(searchQuery);
    final List<KnowledgeChunk> chunks = result.getItems();
    final Map<String, Object> output = new LinkedHashMap<>();
    output.put("chunks", chunks);
    output.put("offset", resolvedOffset);
    output.put("limit", resolvedLimit);
    output.put("hasMore", chunks.size() >= resolvedLimit);
    return ToolOutput.direct(output);
  }

  /**
   * HyDE (Hypothetical Document Embeddings): generates a short passage that plausibly matches
   * {@code query} in the target source's own form, and embeds that instead of the raw query —
   * closing the gap between a short query and the longer passages it's matched against. Grounded
   * with the target knowledge's indexed content preview when the search is scoped to one, so the
   * generated passage's register (prose, dialogue, tabular, etc.) has a real basis rather than a
   * guess. Falls back to the raw query, unmodified, if no fast model is configured or generation
   * fails for any reason — this is a retrieval quality improvement, never a hard dependency for
   * search to work.
   */
  private String hypotheticalPassage(final String query, final String knowledgeId) {
    final String fastModelId = defaultModelsRepository.getFastModelId();
    try (RefCounted<Model.LLMModel> refCounted = modelProvider.get(fastModelId)) {
      final String prompt = hypotheticalPassagePrompt(query, contentPreview(knowledgeId));
      final LlmRequest request =
          LlmRequest.builder()
              .contents(
                  List.of(
                      Content.builder()
                          .role(Constants.AUTHOR_USER)
                          .parts(Part.fromText(prompt))
                          .build()))
              .build();
      final LlmResponse response =
          refCounted
              .value()
              .model()
              .generateContent(request, false)
              .timeout(HYPOTHETICAL_PASSAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
              .blockingSingle();
      final String passage = response.content().map(Content::text).map(String::trim).orElse("");
      return StringUtils.isNotBlank(passage) ? passage : query;
    } catch (final Exception e) {
      LOG.debug("Hypothetical passage generation failed; falling back to the raw query.", e);
      return query;
    }
  }

  private String contentPreview(final String knowledgeId) {
    if (StringUtils.isBlank(knowledgeId)) {
      return null;
    }
    final Knowledge knowledge = knowledgeService.findById(knowledgeId);
    return knowledge == null ? null : knowledge.getContentPreview();
  }

  private static String hypotheticalPassagePrompt(final String query, final String contentPreview) {
    final StringBuilder prompt =
        new StringBuilder()
            .append(
                "Write a short passage (2-4 sentences) that would plausibly appear in a document "
                    + "and match this search topic: \"")
            .append(query)
            .append("\".\n");
    if (StringUtils.isNotBlank(contentPreview)) {
      prompt
          .append(
              "The document begins with the following excerpt — match its exact style, register "
                  + "and format:\n\"")
          .append(contentPreview)
          .append("\"\n");
    }
    prompt.append("Output only the passage itself, with no preamble or explanation.");
    return prompt.toString();
  }

  private String resolveModelId(final ToolContext toolContext) {
    if (toolContext.invocationContext().agent() instanceof Agent agent) {
      final KnowledgeSettings settings = agent.getAgentConfig().getKnowledgeSettings();
      if (settings != null && StringUtils.isNotBlank(settings.getEmbeddingModelId())) {
        return settings.getEmbeddingModelId();
      }
    }
    return defaultModelsRepository.getEmbeddingModelId();
  }
}
