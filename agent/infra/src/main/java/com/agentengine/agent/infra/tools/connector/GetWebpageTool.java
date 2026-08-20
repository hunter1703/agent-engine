package com.agentengine.agent.infra.tools.connector;

import com.agentengine.agent.infra.annotations.DiscoverableTool;
import com.agentengine.agent.infra.annotations.ToolConstructor;
import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.tools.knowledge.SearchKnowledgeTool;
import com.agentengine.knowledge.api.beans.IndexRequest;
import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.common.beans.FileDetails;
import com.google.adk.tools.ToolContext;
import java.util.List;
import java.util.Map;

@DiscoverableTool
public final class GetWebpageTool extends Tool {
  private static final String TOOL_NAME = "get_webpage";
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          TOOL_NAME,
          "Fetches a web page from the provided URL and returns its content as Markdown. "
              + "When indexAsKnowledge is true the page is asynchronously indexed as knowledge and a "
              + "knowledgeId is returned instead of the raw Markdown — use search_knowledge to search it. "
              + "Returns: { markdown, url, statusCode, contentLength } "
              + "or { knowledgeId, hint } when indexAsKnowledge is true.");

  private final KnowledgeService knowledgeService;

  @ToolConstructor
  public GetWebpageTool(KnowledgeService knowledgeService) {
    super(DESCRIPTOR);
    this.knowledgeService = knowledgeService;
  }

  /**
   * Fetches a web page and either converts it to Markdown or indexes it as knowledge.
   *
   * @param url the URL of the web page to fetch
   * @param toolContext the tool execution context; provides the agent and session IDs
   * @return a knowledgeId reference when {@code indexAsKnowledge} is true
   */
  public ToolOutput<?> execute(
      @ToolSchema(
              name = "url",
              description = "The URL of the web page to fetch. Must be a valid HTTP or HTTPS URL.")
          String url,
      ToolContext toolContext) {
    if (StringUtils.isBlank(url)) {
      return ToolOutput.direct(Map.of("error", "URL cannot be null or empty", "statusCode", 400));
    }

    if (!url.startsWith("http")) {
      url = "https://" + url;
    }
    return indexUrl(url, toolContext);
  }

  private ToolOutput<?> indexUrl(final String url, final ToolContext toolContext) {
    final String agentName = toolContext.invocationContext().agent().name();
    final String sessionId = toolContext.invocationContext().session().id();
    final IndexRequest request = new IndexRequest();
    request.setAgentId(agentName);
    request.setFileDetails(FileDetails.fromUrl(url));
    request.setTitle(url);
    request.setGrants(List.of("S/" + sessionId));
    request.setWaitForCompletion(true);
    final Knowledge knowledge = knowledgeService.create(request);
    return ToolOutput.knowledge(
        knowledge.getId(),
        "Webpage : "
            + url
            + " indexed. Use"
            + SearchKnowledgeTool.DESCRIPTOR.name()
            + " with knowledgeId='"
            + knowledge.getId()
            + "' to search for information.");
  }
}
