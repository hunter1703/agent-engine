package com.agentengine.agent.infra.tools.connector;

import com.agentengine.agent.infra.annotations.DiscoverableTool;
import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.knowledge.api.beans.IndexRequest;
import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.tools.ToolArg;
import com.agentengine.util.agents.tools.ToolConstructor;
import com.agentengine.util.cloudstorage.CloudStorageService;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.utils.FileUtils;
import com.agentengine.util.common.utils.HttpUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.tools.ToolContext;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@DiscoverableTool
public final class GetWebpageTool extends Tool {
  private static final String TOOL_NAME = "get_webpage";
  public static final int DEFAULT_MAX_LENGTH = 25_000;

  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          TOOL_NAME,
          """
          Fetches a web page and returns it as Markdown. A page too long to return is indexed as \
          knowledge instead, to be searched with the knowledge search tool.
          Returns: { markdown }, { knowledge_id, hint } for a page indexed as knowledge, or { error } on failure.""");

  private final KnowledgeService knowledgeService;
  private final CloudStorageService cloudStorageService;

  @ToolConstructor
  public GetWebpageTool(
      final KnowledgeService knowledgeService,
      final CloudStorageService cloudStorageService) {
    super(DESCRIPTOR);
    this.knowledgeService = knowledgeService;
    this.cloudStorageService = cloudStorageService;
  }

  public ToolOutput<?> execute(
      @ToolArg(
              name = "url",
              description = "The page's http or https URL; https is assumed when there is no scheme.")
          String url,
      ToolContext toolContext) {
    if (StringUtils.isBlank(url)) {
      return ToolOutput.direct(Map.of("error", "URL cannot be null or empty"));
    }
    final String fetchedUrl = HttpUtils.withScheme(url);

    final String markdown;
    try {
      markdown = HttpUtils.fetchAsText(fetchedUrl);
    } catch (final Exception ex) {
      return ToolOutput.direct(Map.of("error", "Failed to fetch URL: " + ex.getMessage()));
    }

    if (markdown.length() <= DEFAULT_MAX_LENGTH) {
      return ToolOutput.direct(Map.of("markdown", markdown));
    }

    try {
      final byte[] markdownBytes = markdown.getBytes(StandardCharsets.UTF_8);
      final String fileName = FileUtils.deriveFileName(fetchedUrl, "webpage.md");
      final FileDetails fileDetails =
          cloudStorageService.upload(
              fileName,
              new ByteArrayInputStream(markdownBytes),
              (long) markdownBytes.length,
              FileUtils.MARKDOWN_MIME_TYPE);

      final IndexRequest request = new IndexRequest();
      request.setFileDetails(fileDetails);
      request.setTitle(fetchedUrl);
      request.setWaitForCompletion(true);
      final Knowledge knowledge = knowledgeService.index(request);

      final String hint =
          "Webpage '%s' (%d characters) is too long to return, so it was indexed as knowledge. Search it with the knowledge search tool, restricted to this knowledge id."
              .formatted(fetchedUrl, markdown.length());
      return ToolOutput.knowledge(knowledge.getId(), hint);
    } catch (final Exception ex) {
      return ToolOutput.direct(
          Map.of("error", "Failed to index webpage as knowledge: " + ex.getMessage()));
    }
  }
}
