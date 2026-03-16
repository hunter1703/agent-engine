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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tool for listing directory contents with depth control and pagination.
 * Supports recursive listing up to a specified depth.
 */
@DiscoverableTool
public final class ListDirTool extends Tool {
  private static final Logger LOG = LoggerFactory.getLogger(ListDirTool.class);
  private static final String TOOL_NAME = "list_dir";
  private static final int DEFAULT_OFFSET = 1;
  private static final int DEFAULT_LIMIT = 100;
  private static final int DEFAULT_DEPTH = 2;
  private static final int MAX_ENTRY_LENGTH = 500;

  public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_NAME,
      "List contents of a directory. Supports recursive listing with depth control (default: 2 levels) and pagination.", Map.of(),
      ToolRiskLevel.LOW);

  public ListDirTool() {
    super(DESCRIPTOR);
  }

  public Map<String, Object> execute(
      @ToolSchema(name = "dir_path", description = "Absolute or relative path to the directory to list", optional = false) String dirPath,
      @ToolSchema(name = "offset", description = "1-indexed entry number to start from (default: 1)", optional = true) Integer offset,
      @ToolSchema(name = "limit", description = "Maximum number of entries to return (default: 100)", optional = true) Integer limit,
      @ToolSchema(name = "depth", description = "Recursion depth for subdirectories (default: 2, 0 = no recursion)", optional = true) Integer depth,
      ToolContext toolContext) {

    if (dirPath == null || dirPath.isBlank()) {
      return Map.of("error", "dir_path is required");
    }

    int startOffset = offset != null ? Math.max(1, offset) : DEFAULT_OFFSET;
    int maxEntries = limit != null ? Math.max(1, Math.min(limit, 1000)) : DEFAULT_LIMIT;
    int maxDepth = depth != null ? Math.max(0, Math.min(depth, 10)) : DEFAULT_DEPTH;

    try {
      Path dir = resolvePath(dirPath);

      if (!Files.exists(dir)) {
        return Map.of("error", "Directory not found: " + dirPath);
      }

      if (!Files.isDirectory(dir)) {
        return Map.of("error", "Not a directory: " + dirPath);
      }

      List<DirectoryEntry> allEntries = getEntries(dir, dir, 0, maxDepth);

      int totalEntries = allEntries.size();
      int startIndex = Math.max(0, startOffset - 1);
      int endIndex = Math.min(totalEntries, startIndex + maxEntries);

      if (startIndex >= totalEntries) {
        return Map.of("entries", List.of(), "dir_path", dirPath, "offset", startOffset, "limit", maxEntries, "total_entries", totalEntries,
            "message", "Start offset (" + startOffset + ") exceeds directory size (" + totalEntries + " entries)");
      }

      List<DirectoryEntry> selectedEntries = allEntries.subList(startIndex, endIndex);
      List<Map<String, Object>> entriesJson = selectedEntries.stream().map(this::entryToMap).collect(Collectors.toList());

      boolean hasMore = endIndex < totalEntries;

      LOG.info("Listed directory: {} entries {}-{} of {} from {}", endIndex - startIndex, startOffset, endIndex, totalEntries, dirPath);

      Map<String, Object> response = new HashMap<>();
      response.put("entries", entriesJson);
      response.put("dir_path", dirPath);
      response.put("offset", startOffset);
      response.put("limit", maxEntries);
      response.put("total_entries", totalEntries);
      response.put("entries_returned", endIndex - startIndex);
      response.put("has_more", hasMore);

      if (hasMore) {
        response.put("next_offset", endIndex + 1);
      }

      return response;

    } catch (IOException e) {
      LOG.error("Failed to list directory: {}", dirPath, e);
      return Map.of("error", "Failed to list directory: " + e.getMessage());
    }
  }

  private List<DirectoryEntry> getEntries(Path rootDir, Path currentDir, int currentDepth, int maxDepth) throws IOException {
    List<DirectoryEntry> entries = new ArrayList<>();
    if (currentDepth > maxDepth) {
      return entries;
    }

    try (Stream<Path> stream = Files.list(currentDir)) {
      List<Path> paths = stream.sorted().toList();

      for (Path path : paths) {
        String relativePath = rootDir.relativize(path).toString();
        boolean isDirectory = Files.isDirectory(path);
        long size = isDirectory ? 0 : Files.size(path);

        entries.add(new DirectoryEntry(path.getFileName().toString(), relativePath, isDirectory, size, currentDepth));

        // Recurse into subdirectories if depth allows
        if (isDirectory && currentDepth < maxDepth) {
          entries.addAll(getEntries(rootDir, path, currentDepth + 1, maxDepth));
        }
      }
    }
    return entries;
  }

  private Map<String, Object> entryToMap(DirectoryEntry entry) {
    Map<String, Object> map = new HashMap<>();
    map.put("name", truncate(entry.name(), MAX_ENTRY_LENGTH));
    map.put("path", truncate(entry.path(), MAX_ENTRY_LENGTH));
    map.put("type", entry.isDirectory() ? "directory" : "file");
    map.put("depth", entry.depth());

    if (!entry.isDirectory()) {
      map.put("size_bytes", entry.size());
    }

    return map;
  }

  private Path resolvePath(String dirPath) {
    Path path = Paths.get(dirPath);

    if (path.isAbsolute()) {
      return path;
    }

    String cwd = System.getProperty("user.dir", ".");
    return Paths.get(cwd).resolve(dirPath).normalize();
  }

  private String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength) + "...";
  }

  private record DirectoryEntry(String name, String path, boolean isDirectory, long size, int depth) {
  }
}
