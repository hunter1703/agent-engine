package com.agentengine.agent.infra.tools.file;

import com.agentengine.agent.infra.annotations.DiscoverableTool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.annotations.ToolSchema;
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
            "Reads the UTF-8 text content of a file with optional line-range pagination. Use when you have a "
                    + "specific file path and need to examine or extract its content. For locating files or "
                    + "searching across multiple files, use the directory listing or search tools instead. Returns "
                    + "the selected lines along with metadata including total line count, pagination state, and a "
                    + "content hash representing the file's current state at the time of reading. Lines exceeding "
                    + "500 characters are truncated. Tab characters are expanded to 4 spaces. "
                    + "Returns: { content, file_path, offset, limit, total_lines, lines_read, has_more, "
                    + "content_hash (a fingerprint of the file at read time — retain this if you intend to "
                    + "modify the file, to detect whether it changed since you read it), "
                    + "next_offset (when has_more is true), truncated (when any line was cut) }.",
            Map.of(),
            ToolRiskLevel.LOW);

    public ReadFileTool() {
        super(DESCRIPTOR);
    }

    public ToolOutput<Map<String, Object>> execute(
            @ToolSchema(
                            name = "file_path",
                            description =
                                    "Absolute or relative path to the file. Relative paths are resolved against the process working directory.")
                    String filePath,
            @ToolSchema(
                            name = "offset",
                            description =
                                    "1-indexed line number to begin reading from. Line 1 is the first line of the file. "
                                            + "Defaults to 1. Use the next_offset value from a prior response to paginate forward.",
                            optional = true)
                    Integer offset,
            @ToolSchema(
                            name = "limit",
                            description =
                                    "Maximum number of lines to return. When specified, capped at 1,000. Defaults to 2,000.",
                            optional = true)
                    Integer limit) {

        if (filePath == null || filePath.isBlank()) {
            return ToolOutput.direct(Map.of("error", "file_path is required"));
        }

        offset = offset != null ? Math.max(0, offset) : DEFAULT_OFFSET;
        limit = limit != null ? Math.max(1, Math.min(limit, 1000)) : DEFAULT_LIMIT;

        Path path = resolvePath(filePath);

        if (!Files.exists(path)) {
            return ToolOutput.direct(Map.of("error", "File not found: " + filePath));
        }

        if (!Files.isRegularFile(path)) {
            return ToolOutput.direct(Map.of("error", "Not a regular file: " + filePath));
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

        final Map<String, Object> response = new HashMap<>();
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
        return ToolOutput.direct(response);
    }
}
