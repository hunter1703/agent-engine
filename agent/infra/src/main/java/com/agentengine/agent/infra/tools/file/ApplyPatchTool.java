package com.agentengine.agent.infra.tools.file;

import com.agentengine.agent.infra.annotations.DiscoverableTool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.annotations.ToolSchema;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
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
 *
 * <p>Supports two hunk header formats:
 * <ul>
 *   <li><b>Unified diff</b> (standard): {@code @@ -line,count +line,count @@} — line numbers must
 *       match the file at apply time; fails on offset drift.
 *   <li><b>Context-anchor</b>: {@code @@ context: <anchor text>} — locates the hunk by searching
 *       for the anchor string, tolerating line-number drift from prior edits. Prefer this form for
 *       multi-hunk patches or when line numbers may be unreliable.
 * </ul>
 * Both formats may appear in the same patch string.
 */
@DiscoverableTool
public final class ApplyPatchTool extends BaseFileTool {
    private static final Logger LOG = LoggerFactory.getLogger(ApplyPatchTool.class);
    private static final String TOOL_NAME = "apply_patch";
    private static final int MAX_PATCH_SIZE = 100000; // 100KB max patch
    private static final String CONTEXT_ANCHOR_PREFIX = "@@ context:";

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_NAME,
            "Applies a patch to a file, modifying it in-place. Supports two hunk header formats:\n"
                    + "1. Standard unified diff: @@ -line,count +line,count @@ — requires exact line numbers.\n"
                    + "2. Context-anchor: @@ context: <anchor text> — locates each hunk by searching for a "
                    + "distinctive surrounding line, tolerating line-number drift from earlier edits in the same "
                    + "turn. Prefer context-anchor for multi-hunk patches or when line numbers may be unreliable.\n"
                    + "Both formats may be mixed in one patch string.\n"
                    + "Optionally accepts a content hash to guard against stale patches. File writes are atomic. "
                    + "Maximum patch size: 100,000 characters.\n"
                    + "Returns: { success, file_path, additions, deletions, hunks, message } on success, "
                    + "or { error, validation_failed?, rolled_back? } on failure.",
            Map.of(),
            ToolRiskLevel.MEDIUM);

    public ApplyPatchTool() {
        super(DESCRIPTOR);
    }

    public ToolOutput<Map<String, Object>> execute(
            @ToolSchema(
                            name = "file_path",
                            description =
                                    "Absolute or relative path to the file to patch. The file must already exist.")
                    String filePath,
            @ToolSchema(
                            name = "patch",
                            description =
                                    "Unified diff content to apply, including @@ hunk headers. Must be valid unified diff "
                                            + "format. Maximum 100,000 characters.")
                    String patch,
            @ToolSchema(
                            name = "expected_hash",
                            description =
                                    "Optional content hash representing the expected file state before patching. When provided, "
                                            + "the patch is rejected if the file has been modified since this hash was recorded, "
                                            + "preventing accidental modification of an already-changed file.",
                            optional = true)
                    String expectedHash)
            throws PatchFailedException {

        if (filePath == null || filePath.isBlank()) {
            return ToolOutput.direct(Map.of("error", "file_path is required"));
        }

        if (patch == null || patch.isBlank()) {
            return ToolOutput.direct(Map.of("error", "patch is required"));
        }

        if (patch.length() > MAX_PATCH_SIZE) {
            return ToolOutput.direct(
                    Map.of("error", "Patch too large: " + patch.length() + " chars (max: " + MAX_PATCH_SIZE + ")"));
        }

        try {
            final Path file = resolvePath(filePath);

            if (!Files.exists(file)) {
                return ToolOutput.direct(Map.of("error", "File not found: " + filePath));
            }

            final BaseFileTool.FileDetails details = readFile(file, 0, 0);
            final String currentHash = details.hash();
            if (expectedHash != null && !expectedHash.equalsIgnoreCase(currentHash)) {
                return ToolOutput.direct(Map.of(
                        "error",
                        "File content has changed since patch was created (hash mismatch)",
                        "validation_failed",
                        true,
                        "expected_hash",
                        expectedHash,
                        "actual_hash",
                        currentHash));
            }

            final String originalContent = Files.readString(file);
            final List<String> originalLines = Arrays.asList(originalContent.split("\n"));

            final NormalizedPatch normalized = normalizeToUnifiedDiff(originalLines, Arrays.asList(patch.split("\n")));
            if (normalized.error() != null) {
                return ToolOutput.direct(Map.of("error", normalized.error(), "success", false));
            }

            final Patch<String> parsedPatch;
            try {
                parsedPatch = UnifiedDiffUtils.parseUnifiedDiff(normalized.lines());
            } catch (Exception exception) {
                return ToolOutput.direct(
                        Map.of("error", "Invalid unified diff format: " + exception.getMessage(), "success", false));
            }

            final List<String> patchedLines = DiffUtils.patch(originalLines, parsedPatch);
            // Join lines back, preserving original line ending style
            String patchedContent = String.join("\n", patchedLines);
            if (!originalContent.endsWith("\n") && patchedContent.endsWith("\n")) {
                patchedContent = patchedContent.substring(0, patchedContent.length() - 1);
            }

            // Atomic write with backup
            final Path backupFile = file.resolveSibling(file.getFileName().toString() + ".patch-backup");
            Files.copy(file, backupFile, StandardCopyOption.REPLACE_EXISTING);

            try {
                Files.writeString(file, patchedContent);
                try {
                    Files.deleteIfExists(backupFile);
                } catch (IOException exception) {
                    LOG.warn("Failed to delete backup file: {}", backupFile, exception);
                }

                final int hunkCount = parsedPatch.getDeltas().size();
                final int additions = patchedLines.size()
                        - originalLines.size()
                        + parsedPatch.getDeltas().stream()
                                .mapToInt(delta -> delta.getSource().size())
                                .sum();
                final int deletions = parsedPatch.getDeltas().stream()
                        .mapToInt(delta -> delta.getSource().size())
                        .sum();
                LOG.info("Applied patch to {}: {} additions, {} deletions", filePath, additions, deletions);

                final Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("file_path", filePath);
                response.put("additions", additions);
                response.put("deletions", deletions);
                response.put("hunks", hunkCount);
                response.put("message", "Patch applied successfully");
                return ToolOutput.direct(response);

            } catch (IOException exception) {
                LOG.error("Failed to write patched content, rolling back", exception);
                Files.copy(backupFile, file, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(backupFile);
                return ToolOutput.direct(Map.of(
                        "error", "Failed to write patched file: " + exception.getMessage(),
                        "rolled_back", true,
                        "success", false));
            }

        } catch (IOException exception) {
            LOG.error("Failed to apply patch to {}", filePath, exception);
            return ToolOutput.direct(Map.of("error", "Failed to apply patch: " + exception.getMessage()));
        }
    }

    /**
     * Converts any {@code @@ context: <anchor>} hunk headers to standard {@code @@ -l,c +l,c @@}
     * headers by locating the anchor line in the original file. The anchor becomes a leading context
     * line in the synthesized hunk. Standard unified diff hunks pass through unchanged.
     */
    private static NormalizedPatch normalizeToUnifiedDiff(
            final List<String> originalLines, final List<String> patchLines) {
        final boolean hasContextAnchors = patchLines.stream()
                .anyMatch(line -> line.toLowerCase().startsWith(CONTEXT_ANCHOR_PREFIX.toLowerCase()));
        if (!hasContextAnchors) {
            return NormalizedPatch.success(patchLines);
        }

        final List<String> output = new ArrayList<>();
        // parseUnifiedDiff requires --- +++ headers as a start-of-diff signal; filename is ignored.
        if (patchLines.stream().noneMatch(line -> line.startsWith("---"))) {
            output.add("--- file");
            output.add("+++ file");
        }

        int i = 0;
        while (i < patchLines.size()) {
            final String line = patchLines.get(i);
            if (!line.toLowerCase().startsWith(CONTEXT_ANCHOR_PREFIX.toLowerCase())) {
                output.add(line);
                i++;
                continue;
            }

            final String anchor = line.substring(CONTEXT_ANCHOR_PREFIX.length()).strip();
            if (anchor.isEmpty()) {
                return NormalizedPatch.error("Empty anchor in hunk header: " + line);
            }

            final int anchorIndex = findAnchorLine(originalLines, anchor);
            if (anchorIndex == -1) {
                return NormalizedPatch.error("Anchor not found in file: \"" + anchor + "\"");
            }
            if (anchorIndex == -2) {
                return NormalizedPatch.error("Ambiguous anchor — multiple matches for: \"" + anchor + "\"");
            }

            // Collect hunk body until the next @@ header or end of patch
            final List<String> body = new ArrayList<>();
            i++;
            while (i < patchLines.size() && !patchLines.get(i).startsWith("@@")) {
                body.add(patchLines.get(i));
                i++;
            }

            final long oldCount =
                    1 + body.stream().filter(l -> !l.startsWith("+")).count();
            final long newCount =
                    1 + body.stream().filter(l -> !l.startsWith("-")).count();
            output.add("@@ -%d,%d +%d,%d @@".formatted(anchorIndex + 1, oldCount, anchorIndex + 1, newCount));
            output.add(" " + anchor);
            output.addAll(body);
        }

        return NormalizedPatch.success(output);
    }

    private static int findAnchorLine(final List<String> lines, final String anchor) {
        int found = -1;
        int count = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).equals(anchor) || lines.get(i).strip().equals(anchor.strip())) {
                found = i;
                count++;
            }
        }
        if (count == 0) return -1;
        if (count > 1) return -2;
        return found;
    }

    private record NormalizedPatch(List<String> lines, String error) {
        static NormalizedPatch success(final List<String> lines) {
            return new NormalizedPatch(lines, null);
        }

        static NormalizedPatch error(final String message) {
            return new NormalizedPatch(null, message);
        }
    }
}
