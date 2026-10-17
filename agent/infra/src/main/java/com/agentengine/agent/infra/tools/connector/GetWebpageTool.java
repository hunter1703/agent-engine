package com.agentengine.agent.infra.tools.connector;

import com.agentengine.agent.infra.annotations.DiscoverableTool;
import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.tools.ToolArg;
import com.agentengine.util.agents.tools.ToolConstructor;
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.utils.MarkdownUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.tools.ToolContext;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@DiscoverableTool
public final class GetWebpageTool extends Tool {
  private static final String TOOL_NAME = "get_webpage";
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          TOOL_NAME,
          "Fetches a web page from the provided URL and returns its content as Markdown. "
              + "Returns: { markdown, url, contentLength } or { error, url }.");

  private final FileService fileService;

  @ToolConstructor
  public GetWebpageTool(final FileService fileService) {
    super(DESCRIPTOR);
    this.fileService = fileService;
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
      return ToolOutput.direct(
          Map.of("error", "Failed to fetch URL: " + ex.getMessage(), "url", url));
    }
    final String markdown = MarkdownUtils.fromHtml(html);
    return ToolOutput.direct(
        Map.of("markdown", markdown, "url", url, "contentLength", markdown.length()));
  }
}
