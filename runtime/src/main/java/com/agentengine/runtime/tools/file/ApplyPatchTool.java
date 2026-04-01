package com.agentengine.runtime.tools.file;

import com.agentengine.runtime.annotations.DiscoverableTool;
import com.agentengine.runtime.annotations.ToolSchema;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tool for applying unified diff patches to files using java-diff-utils library. Validates patch
 * syntax, optionally verifies file hasn't changed via hash, and performs atomic file updates with
 * rollback on failure.
 */
@DiscoverableTool
public final class ApplyPatchTool extends BaseFileTool {
    private static final Logger LOG = LoggerFactory.getLogger(ApplyPatchTool.class);
    private static final String TOOL_NAME = "apply_patch";
    private static final int MAX_PATCH_SIZE = 100000; // 100KB max patch

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_NAME,
            "Apply a unified diff patch to a file. Validates patch syntax, optionally verifies file hasn't changed via "
                    + "content_hash from read_file, and performs atomic file updates with rollback on failure.",
            Map.of(),
            ToolRiskLevel.MEDIUM);

    public ApplyPatchTool() {
        super(DESCRIPTOR);
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "file_path", description = "Path to the file to patch") String filePath,
            @ToolSchema(name = "patch", description = "Unified diff patch content to apply") String patch,
            @ToolSchema(
                            name = "expected_hash",
                            description =
                                    "Expected content hash from read_file (first 16 chars of SHA-256). If provided, validates file hasn't changed.",
                            optional = true)
                    String expectedHash) {

        if (filePath == null || filePath.isBlank()) {
            return Map.of("error", "file_path is required");
        }

        if (patch == null || patch.isBlank()) {
            return Map.of("error", "patch is required");
        }

        if (patch.length() > MAX_PATCH_SIZE) {
            return Map.of("error", "Patch too large: " + patch.length() + " chars (max: " + MAX_PATCH_SIZE + ")");
        }

        try {
            Path file = resolvePath(filePath);

            if (!Files.exists(file)) {
                return Map.of("error", "File not found: " + filePath);
            }

            BaseFileTool.FileDetails details = readFile(file, 0, 0);
            String currentHash = details.getHash();
            if (expectedHash != null && !expectedHash.equalsIgnoreCase(currentHash)) {
                return Map.of(
                        "error",
                        "File content has changed since patch was created (hash mismatch)",
                        "validation_failed",
                        true,
                        "expected_hash",
                        expectedHash,
                        "actual_hash",
                        currentHash);
            }

            List<String> patchLines = Arrays.asList(patch.split("\n"));
            Patch<String> parsedPatch;
            try {
                parsedPatch = UnifiedDiffUtils.parseUnifiedDiff(patchLines);
            } catch (Exception exception) {
                return Map.of("error", "Invalid unified diff format: " + exception.getMessage(), "success", false);
            }

            final String originalContent = Files.readString(file);
            List<String> originalLines = Arrays.asList(originalContent.split("\n"));
            List<String> patchedLines;
            try {
                patchedLines = DiffUtils.patch(originalLines, parsedPatch);
            } catch (PatchFailedException exception) {
                return Map.of(
                        "error",
                        "Failed to apply patch: " + exception.getMessage(),
                        "success",
                        false,
                        "reason",
                        "Patch context mismatch - file may have changed");
            }

            // Join lines back, preserving original line ending style
            String patchedContent = String.join("\n", patchedLines);
            // Remove trailing newline if original didn't have one
            if (!originalContent.endsWith("\n") && patchedContent.endsWith("\n")) {
                patchedContent = patchedContent.substring(0, patchedContent.length() - 1);
            }

            // Create backup for rollback
            Path backupFile = file.resolveSibling(file.getFileName().toString() + ".patch-backup");
            Files.copy(file, backupFile, StandardCopyOption.REPLACE_EXISTING);

            try {
                // Write patched content
                Files.writeString(file, patchedContent);

                // Clean up backup on success
                try {
                    Files.deleteIfExists(backupFile);
                } catch (IOException exception) {
                    LOG.warn("Failed to delete backup file: {}", backupFile, exception);
                }

                // Calculate stats
                int additions = (int) patchedLines.stream()
                        .filter(line -> line.startsWith("+") && !line.startsWith("+++"))
                        .count();
                int deletions = originalContent.split("\n").length - patchedLines.size() + additions;

                LOG.info(
                        "Successfully applied patch to {}: {} additions, {} deletions", filePath, additions, deletions);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("file_path", filePath);
                response.put("additions", additions);
                response.put("deletions", deletions);
                response.put("hunks", parsedPatch.getDeltas().size());
                response.put("message", "Patch applied successfully");

                return response;

            } catch (IOException exception) {
                // Rollback on failure
                LOG.error("Failed to write patched content, rolling back", exception);
                Files.copy(backupFile, file, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(backupFile);

                return Map.of(
                        "error",
                        "Failed to write patched file: " + exception.getMessage(),
                        "rolled_back",
                        true,
                        "success",
                        false);
            }

        } catch (IOException exception) {
            LOG.error("Failed to apply patch to {}", filePath, exception);
            return Map.of("error", "Failed to apply patch: " + exception.getMessage());
        }
    }
}
