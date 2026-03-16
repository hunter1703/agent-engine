package com.agentengine.engine.tools.fileops;

import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.plugin.annotations.DiscoverableTool;
import com.agentengine.engine.plugin.annotations.ToolSchema;
import com.agentengine.engine.plugin.tools.Tool;
import com.agentengine.engine.api.tools.ToolRiskLevel;
import com.google.adk.tools.ToolContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tool for reading file contents with pagination support. Supports reading by
 * line ranges with configurable offset and limit.
 */
@DiscoverableTool
public final class ReadFileTool extends Tool {
  private static final Logger LOG = LoggerFactory.getLogger(ReadFileTool.class);
  private static final String TOOL_NAME = "read_file";
  private static final int DEFAULT_OFFSET = 1;
  private static final int DEFAULT_LIMIT = 2000;
  private static final int MAX_LINE_LENGTH = 500;
  private static final int TAB_WIDTH = 4;

  public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_NAME,
      "Read contents of a file with optional line range pagination. Use offset (1-indexed) and limit to read specific portions of large files.",
      Map.of(), ToolRiskLevel.LOW);

  public ReadFileTool() {
    super(DESCRIPTOR);
  }

  public Map<String, Object> execute(
      @ToolSchema(name = "file_path", description = "Absolute or relative path to the file to read", optional = false) String filePath,
      @ToolSchema(name = "offset", description = "1-indexed line number to start reading from (default: 1)", optional = true) Integer offset,
      @ToolSchema(name = "limit", description = "Maximum number of lines to return (default: 2000)", optional = true) Integer limit,
      ToolContext toolContext) {

    if (filePath == null || filePath.isBlank()) {
      return Map.of("error", "file_path is required");
    }

    int startLine = offset != null ? Math.max(1, offset) : DEFAULT_OFFSET;
    int maxLines = limit != null ? Math.max(1, Math.min(limit, 10000)) : DEFAULT_LIMIT;

    try {
      Path path = resolvePath(filePath);

      if (!Files.exists(path)) {
        return Map.of("error", "File not found: " + filePath);
      }

      if (!Files.isRegularFile(path)) {
        return Map.of("error", "Not a regular file: " + filePath);
      }

      List<String> allLines = Files.readAllLines(path);
      int totalLines = allLines.size();

      // Calculate actual range (convert 1-indexed to 0-indexed)
      int startIndex = Math.max(0, startLine - 1);
      int endIndex = Math.min(totalLines, startIndex + maxLines);

      if (startIndex >= totalLines) {
        return Map.of("content", "", "file_path", filePath, "offset", startLine, "limit", maxLines, "total_lines", totalLines, "message",
            "Start offset (" + startLine + ") exceeds file length (" + totalLines + " lines)");
      }

      List<String> selectedLines = allLines.subList(startIndex, endIndex);
      StringBuilder content = new StringBuilder();
      boolean truncated = false;

      for (String line : selectedLines) {
        String processedLine = processLine(line);
        if (processedLine.length() > MAX_LINE_LENGTH) {
          processedLine = processedLine.substring(0, MAX_LINE_LENGTH) + "... [truncated]";
          truncated = true;
        }
        content.append(processedLine).append("\n");
      }

      String result = content.toString();
      boolean hasMore = endIndex < totalLines;

      LOG.info("Read file: {} lines {}-{} of {} from {}", endIndex - startIndex, startLine, endIndex, totalLines, filePath);

      Map<String, Object> response = new java.util.HashMap<>();
      response.put("content", result);
      response.put("file_path", filePath);
      response.put("offset", startLine);
      response.put("limit", maxLines);
      response.put("total_lines", totalLines);
      response.put("lines_read", endIndex - startIndex);
      response.put("has_more", hasMore);

      if (truncated) {
        response.put("truncated", true);
        response.put("max_line_length", MAX_LINE_LENGTH);
      }

      if (hasMore) {
        response.put("next_offset", endIndex + 1);
      }

      return response;

    } catch (IOException e) {
      LOG.error("Failed to read file: {}", filePath, e);
      return Map.of("error", "Failed to read file: " + e.getMessage());
    }
  }

  private Path resolvePath(String filePath) {
    Path path = Paths.get(filePath);

    // If absolute path, use as-is
    if (path.isAbsolute()) {
      return path;
    }

    // Try to get working directory from tool context or use current directory
    String cwd = System.getProperty("user.dir", ".");
    return Paths.get(cwd).resolve(filePath).normalize();
  }

  private String processLine(String line) {
    // Expand tabs to spaces for consistent display
    return line.replace("\t", " ".repeat(TAB_WIDTH));
  }
}
