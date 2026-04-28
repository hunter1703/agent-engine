package com.agentengine.agent.infra.tools.file;

import com.agentengine.agent.infra.annotations.DiscoverableTool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.annotations.ToolSchema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tool for listing directory contents with depth control and pagination. Supports recursive listing
 * up to a specified depth.
 */
@DiscoverableTool
public final class ListDirTool extends BaseFileTool {
    private static final Logger LOG = LoggerFactory.getLogger(ListDirTool.class);
    private static final String TOOL_NAME = "list_dir";
    private static final int DEFAULT_OFFSET = 1;
    private static final int DEFAULT_LIMIT = 100;
    private static final int DEFAULT_DEPTH = 2;
    private static final int MAX_ENTRY_LENGTH = 500;

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_NAME,
            "Lists the contents of a directory, optionally traversing subdirectories up to a configurable depth. "
                    + "Use to understand directory structure, discover what files are present, or browse for files "
                    + "by name when you do not know the exact path. For searching file contents by pattern, use the "
                    + "search tool instead. Entries are sorted alphabetically at each level and include their type "
                    + "(file or directory), relative path, depth level, and file size in bytes (files only). "
                    + "Supports pagination for large directories. "
                    + "Returns: { entries: [{name, path, type, depth, size_bytes?}], dir_path, offset, limit, "
                    + "total_entries, entries_returned, has_more, next_offset (when has_more is true) }.",
            Map.of(),
            ToolRiskLevel.LOW);

    public ListDirTool() {
        super(DESCRIPTOR);
    }

    public ToolOutput<Map<String, Object>> execute(
            @ToolSchema(
                            name = "dir_path",
                            description =
                                    "Absolute or relative path to the directory to list. Relative paths are resolved "
                                            + "against the process working directory.")
                    String dirPath,
            @ToolSchema(
                            name = "offset",
                            description =
                                    "1-indexed position of the first entry to return across the full flattened listing. Defaults to 1.",
                            optional = true)
                    Integer offset,
            @ToolSchema(
                            name = "limit",
                            description = "Maximum number of entries to return. Capped at 1,000. Defaults to 100.",
                            optional = true)
                    Integer limit,
            @ToolSchema(
                            name = "depth",
                            description =
                                    "Maximum recursion depth into subdirectories. 0 lists only the immediate contents of "
                                            + "dir_path. Defaults to 2. Capped at 10.",
                            optional = true)
                    Integer depth) {

        if (dirPath == null || dirPath.isBlank()) {
            return ToolOutput.direct(Map.of("error", "dir_path is required"));
        }

        int startOffset = offset != null ? Math.max(1, offset) : DEFAULT_OFFSET;
        int maxEntries = limit != null ? Math.max(1, Math.min(limit, 1000)) : DEFAULT_LIMIT;
        int maxDepth = depth != null ? Math.max(0, Math.min(depth, 10)) : DEFAULT_DEPTH;

        try {
            Path dir = resolvePath(dirPath);

            if (!Files.exists(dir)) {
                return ToolOutput.direct(Map.of("error", "Directory not found: " + dirPath));
            }

            if (!Files.isDirectory(dir)) {
                return ToolOutput.direct(Map.of("error", "Not a directory: " + dirPath));
            }

            List<DirectoryEntry> allEntries = getEntries(dir, dir, 0, maxDepth);

            int totalEntries = allEntries.size();
            int startIndex = Math.max(0, startOffset - 1);
            int endIndex = Math.min(totalEntries, startIndex + maxEntries);

            if (startIndex >= totalEntries) {
                return ToolOutput.direct(Map.of(
                        "entries",
                        List.of(),
                        "dir_path",
                        dirPath,
                        "offset",
                        startOffset,
                        "limit",
                        maxEntries,
                        "total_entries",
                        totalEntries,
                        "message",
                        "Start offset (" + startOffset + ") exceeds directory size (" + totalEntries + " entries)"));
            }

            List<DirectoryEntry> selectedEntries = allEntries.subList(startIndex, endIndex);
            List<Map<String, Object>> entriesJson =
                    selectedEntries.stream().map(this::entryToMap).collect(Collectors.toList());

            boolean hasMore = endIndex < totalEntries;

            LOG.info(
                    "Listed directory: {} entries {}-{} of {} from {}",
                    endIndex - startIndex,
                    startOffset,
                    endIndex,
                    totalEntries,
                    dirPath);

            final Map<String, Object> response = new HashMap<>();
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

            return ToolOutput.direct(response);

        } catch (IOException exception) {
            LOG.error("Failed to list directory: {}", dirPath, exception);
            return ToolOutput.direct(Map.of("error", "Failed to list directory: " + exception.getMessage()));
        }
    }

    private List<DirectoryEntry> getEntries(Path rootDir, Path currentDir, int currentDepth, int maxDepth)
            throws IOException {
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

                entries.add(new DirectoryEntry(
                        path.getFileName().toString(), relativePath, isDirectory, size, currentDepth));

                // Recurse into subdirectories if depth allows
                if (isDirectory && currentDepth < maxDepth) {
                    entries.addAll(getEntries(rootDir, path, currentDepth + 1, maxDepth));
                }
            }
        }
        return entries;
    }

    private Map<String, Object> entryToMap(DirectoryEntry entry) {
        Map<String, Object> entryMap = new HashMap<>();
        entryMap.put("name", truncate(entry.name(), MAX_ENTRY_LENGTH));
        entryMap.put("path", truncate(entry.path(), MAX_ENTRY_LENGTH));
        entryMap.put("type", entry.isDirectory() ? "directory" : "file");
        entryMap.put("depth", entry.depth());

        if (!entry.isDirectory()) {
            entryMap.put("size_bytes", entry.size());
        }

        return entryMap;
    }

    private record DirectoryEntry(String name, String path, boolean isDirectory, long size, int depth) {}
}
