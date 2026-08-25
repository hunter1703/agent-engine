package com.agentengine.agent.infra.tools.knowledge;

import com.agentengine.agent.infra.annotations.DiscoverableTool;
import com.agentengine.agent.infra.annotations.ToolConstructor;
import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.utils.ExtendedRunConfig;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.common.service.CloudStorageService;
import com.google.adk.tools.ToolContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Fetches a granted knowledge source's content on demand; grants are never delivered eagerly. */
@DiscoverableTool
public final class ReadKnowledgeSourceTool extends Tool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.READ_KNOWLEDGE_SOURCE_TOOL_NAME,
          "Reads the full content of a knowledge source you have access to. Not for a knowledgeId "
              + "(a searchable item) — search that with "
              + Constants.SEARCH_KNOWLEDGE_TOOL_NAME
              + " instead. "
              + "Returns: { status: \"success\", content } or { error } if you weren't granted it.",
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
              name = Constants.ARG_SOURCE,
              description = "The exact knowledge source string you have access to.")
          final String source) {
    final boolean granted =
        toolContext.invocationContext().runConfig() instanceof ExtendedRunConfig extended
            && extended.grants().knowledgeSources().contains(source);
    if (!granted) {
      return ToolOutput.direct(Map.of("error", "Not granted access to this knowledge source."));
    }
    final CloudStorageService.Content content = cloudStorageService.download(source);
    try (InputStream stream = content.stream()) {
      final String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      return ToolOutput.direct(Map.of("status", "success", "content", text));
    } catch (final IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
