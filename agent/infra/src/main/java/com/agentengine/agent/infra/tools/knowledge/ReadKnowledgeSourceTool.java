package com.agentengine.agent.infra.tools.knowledge;

import com.agentengine.agent.infra.annotations.DiscoverableTool;
import com.agentengine.agent.infra.annotations.ToolConstructor;
import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.utils.ExtendedRunConfig;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.common.FileUtils;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.common.service.CloudStorageService;
import com.google.adk.tools.ToolContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/** Fetches a granted knowledge source's content on demand; grants are never delivered eagerly. */
@DiscoverableTool
public final class ReadKnowledgeSourceTool extends Tool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.READ_KNOWLEDGE_SOURCE,
          "Reads the full content of a knowledge source you have access to. Not for a knowledgeId "
              + "(a searchable item) — search that with "
              + Constants.ToolNames.SEARCH_KNOWLEDGE
              + " instead. "
              + "Returns: { status: \"success\", content, encoding, mime_type } — encoding is "
              + "\"utf-8\" for text sources or \"base64\" otherwise — or { error } if you weren't "
              + "granted it.",
          Map.of());

  private final CloudStorageService cloudStorageService;

  @ToolConstructor
  public ReadKnowledgeSourceTool(final CloudStorageService cloudStorageService) {
    super(DESCRIPTOR);
    this.cloudStorageService = cloudStorageService;
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolSchema(
              name = Constants.ToolArgs.SOURCE,
              description = "The exact knowledge source string you have access to.")
          final String source) {
    final boolean granted =
        toolContext.invocationContext().runConfig() instanceof ExtendedRunConfig extended
            && extended.grants().knowledgeSources().contains(source);
    if (!granted) {
      return ToolOutput.direct(Map.of("error", "Not granted access to this knowledge source."));
    }
    final CloudStorageService.Content content = cloudStorageService.download(source);
    final boolean isText = FileUtils.isTextFile(content.mimeType(), source);
    try (InputStream stream = content.stream()) {
      final byte[] bytes = stream.readAllBytes();
      final String value =
          isText
              ? new String(bytes, StandardCharsets.UTF_8)
              : Base64.getEncoder().encodeToString(bytes);
      return ToolOutput.direct(
          Map.of(
              "status",
              "success",
              "content",
              value,
              "encoding",
              isText ? "utf-8" : "base64",
              "mime_type",
              content.mimeType() == null ? "" : content.mimeType()));
    } catch (final IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
