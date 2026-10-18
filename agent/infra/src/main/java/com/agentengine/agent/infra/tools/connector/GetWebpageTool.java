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
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.utils.FileUtils;
import com.agentengine.util.common.utils.MarkdownUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.tools.ToolContext;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
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
          Fetches a web page from the provided URL, converts its content to Markdown, and returns it.
          If the converted content exceeds 25,000 characters, it is automatically indexed as knowledge
          to conserve context, and the tool returns a knowledgeId with guidance on how to query its content
          using semantic knowledge search.
          Returns: { markdown, chars_read } when within limit, or { knowledge_id, hint } when indexed as knowledge, or { error } on failure.""");

  private final FileService fileService;
  private final KnowledgeService knowledgeService;
  private final CloudStorageService cloudStorageService;

  @ToolConstructor
  public GetWebpageTool(
      final FileService fileService,
      final KnowledgeService knowledgeService,
      final CloudStorageService cloudStorageService) {
    super(DESCRIPTOR);
    this.fileService = fileService;
    this.knowledgeService = knowledgeService;
    this.cloudStorageService = cloudStorageService;
  }

  public ToolOutput<?> execute(
      @ToolArg(
              name = "url",
              description = "The URL of the web page to fetch. Must be a valid HTTP or HTTPS URL.")
          String url,
      ToolContext toolContext) {
    if (StringUtils.isBlank(url)) {
      return ToolOutput.direct(Map.of("error", "URL cannot be null or empty"));
    }
    if (!url.startsWith("http")) {
      url = "https://" + url;
    }

    final String html;
    try (InputStream content = fileService.getContent(FileDetails.fromUrl(url))) {
      html = new String(content.readAllBytes(), StandardCharsets.UTF_8);
    } catch (final Exception ex) {
      return ToolOutput.direct(Map.of("error", "Failed to fetch URL: " + ex.getMessage()));
    }
    final String markdown = MarkdownUtils.fromHtml(html);

    if (markdown.length() <= DEFAULT_MAX_LENGTH) {
      return ToolOutput.direct(
          Map.of(
              "markdown", markdown,
              "chars_read", markdown.length()));
    }

    try {
      final byte[] markdownBytes = markdown.getBytes(StandardCharsets.UTF_8);
      final String fileName = FileUtils.deriveFileName(url, "webpage.md");
      final FileDetails fileDetails =
          cloudStorageService.upload(
              fileName,
              new ByteArrayInputStream(markdownBytes),
              (long) markdownBytes.length,
              FileUtils.MARKDOWN_MIME_TYPE);

      final IndexRequest request = new IndexRequest();
      request.setFileDetails(fileDetails);
      request.setTitle(url);
      request.setWaitForCompletion(true);
      final Knowledge knowledge = knowledgeService.index(request);

      final String hint =
          "Webpage content from '%s' (%d characters) exceeded 25,000 characters and was automatically indexed as knowledge to conserve context. Query its content using semantic knowledge search (filtered by knowledge ID '%s')."
              .formatted(url, markdown.length(), knowledge.getId());
      return ToolOutput.knowledge(knowledge.getId(), hint);
    } catch (final Exception ex) {
      return ToolOutput.direct(
          Map.of(
              "error",
              "Failed to index webpage as knowledge: " + ex.getMessage(),
              "chars_read",
              markdown.length()));
    }
  }
}
