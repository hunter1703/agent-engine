package com.agentengine.runtime.tools.file;

import com.agentengine.runtime.annotations.DiscoverableTool;
import com.agentengine.runtime.annotations.ToolSchema;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tool for reading file contents with pagination support. Supports reading by line ranges with
 * configurable offset and limit. Returns content hash for patch validation.
 */
@DiscoverableTool
public final class ReadFileTool extends BaseFileTool {
    private static final Logger LOG = LoggerFactory.getLogger(ReadFileTool.class);
    private static final String TOOL_NAME = "read_file";
    private static final int DEFAULT_OFFSET = 1;
    private static final int DEFAULT_LIMIT = 2000;
    private static final int MAX_LINE_LENGTH = 500;
    private static final int TAB_WIDTH = 4;

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_NAME,
            "Read contents of a file with optional line range pagination. Use offset (1-indexed) and limit to read specific portions of large files. "
                    + "Returns content_hash (SHA-256, first 16 chars) for use with apply_patch validation.",
            Map.of(),
            ToolRiskLevel.LOW);

    public ReadFileTool() {
        super(DESCRIPTOR);
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "file_path", description = "Absolute or relative path to the file to read")
                    String filePath,
            @ToolSchema(
                            name = "offset",
                            description = "0-indexed line number to start reading from (default: 0)",
                            optional = true)
                    Integer offset,
            @ToolSchema(
                            name = "limit",
                            description = "Maximum number of lines to return (default: 50)",
                            optional = true)
                    Integer limit) {

        if (filePath == null || filePath.isBlank()) {
            return Map.of("error", "file_path is required");
        }

        offset = offset != null ? Math.max(0, offset) : DEFAULT_OFFSET;
        limit = limit != null ? Math.max(1, Math.min(limit, 1000)) : DEFAULT_LIMIT;

        Path path = resolvePath(filePath);

        if (!Files.exists(path)) {
            return Map.of("error", "File not found: " + filePath);
        }

        if (!Files.isRegularFile(path)) {
            return Map.of("error", "Not a regular file: " + filePath);
        }

        final FileDetails fileDetails = readFile(path, offset, limit);
        List<String> selectedLines = fileDetails.getContent();
        StringBuilder content = new StringBuilder();
        boolean truncated = false;

        for (String line : selectedLines) {
            line = line.replace("\t", " ".repeat(TAB_WIDTH));
            if (line.length() > MAX_LINE_LENGTH) {
                line = truncate(line, MAX_LINE_LENGTH);
                truncated = true;
            }
            content.append(line).append("\n");
        }

        String result = content.toString();
        boolean hasMore = offset + limit <= fileDetails.getNumLines();

        Map<String, Object> response = new HashMap<>();
        response.put("content", result);
        response.put("file_path", filePath);
        response.put("offset", offset);
        response.put("limit", limit);
        response.put("total_lines", fileDetails.getNumLines());
        response.put("lines_read", fileDetails.getContent().size());
        response.put("has_more", hasMore);
        response.put("content_hash", fileDetails.getHash());

        if (truncated) {
            response.put("truncated", true);
            response.put("max_line_length", MAX_LINE_LENGTH);
        }

        if (hasMore) {
            response.put("next_offset", offset + limit);
        }
        return response;
    }
}
