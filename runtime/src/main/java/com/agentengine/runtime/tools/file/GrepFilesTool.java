package com.agentengine.runtime.tools.file;

import com.agentengine.runtime.annotations.DiscoverableTool;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.JsonUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tool for searching file contents using regex patterns. Similar to grep/ripgrep with configurable
 * limits and file filtering.
 */
@DiscoverableTool
public final class GrepFilesTool extends BaseFileTool {
    private static final Logger LOG = LoggerFactory.getLogger(GrepFilesTool.class);
    private static final String TOOL_NAME = "grep_files";
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 2000;
    private static final int MAX_LINE_LENGTH = 500;

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_NAME,
            "Searches the text content of files under a directory tree for lines matching a regular expression. "
                    + "Use when you need to find which files contain a pattern, or locate all occurrences of a "
                    + "string, symbol, or keyword across a codebase — particularly when you do not know which "
                    + "specific file to look in. Returns matching lines with their relative file paths, line "
                    + "numbers, and the matched text segment. Searches are case-insensitive by default. Hidden "
                    + "files and directories (names starting with '.' or '_') are automatically skipped. Binary or "
                    + "unreadable files are silently skipped. Results are capped at the configured limit. "
                    + "Returns: { matches: [{file, lineNumber, line, matchedText}], pattern, search_path, "
                    + "files_searched, total_matches, matches_returned, truncated }.",
            Map.of(),
            ToolRiskLevel.LOW);

    public GrepFilesTool() {
        super(DESCRIPTOR);
    }

    public Map<String, Object> execute(
            @ToolSchema(
                            name = "pattern",
                            description =
                                    "regular expression matched against individual lines. The full line "
                                            + "content is searched; ^ and $ anchors are supported. Special regex characters must be escaped.")
                    String pattern,
            @ToolSchema(
                            name = "path",
                            description =
                                    "Root directory from which to start the recursive search. Defaults to the process "
                                            + "working directory. Must be an existing directory.",
                            optional = true)
                    String searchPath,
            @ToolSchema(
                            name = "include",
                            description =
                                    "Glob pattern to restrict which files are searched (e.g., '*.java', '*.md', 'src/**/*.ts'). "
                                            + "When no directory separator is present, matched against the file name only. "
                                            + "Defaults to all files.",
                            optional = true)
                    String includePattern,
            @ToolSchema(
                            name = "limit",
                            description =
                                    "Maximum number of matching lines to return. Capped at 2,000. Defaults to 100. "
                                            + "When the result is truncated, the response includes truncated: true.",
                            optional = true)
                    Integer limit,
            @ToolSchema(
                            name = "case_sensitive",
                            description =
                                    "Whether the regex match is case-sensitive. Defaults to false (case-insensitive).",
                            optional = true)
                    Boolean caseSensitive) {

        if (pattern == null || pattern.isBlank()) {
            return Map.of("error", "pattern is required");
        }

        final int maxMatches = limit != null ? Math.max(1, Math.min(limit, MAX_LIMIT)) : DEFAULT_LIMIT;
        final boolean isCaseSensitive = caseSensitive != null && caseSensitive;
        final String basePath = searchPath != null ? searchPath : System.getProperty("user.dir", ".");

        try {
            final Pattern regexPattern = Pattern.compile(pattern, isCaseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
            final Path baseDir = resolvePath(basePath);

            if (!Files.exists(baseDir)) {
                return Map.of("error", "Search path not found: " + basePath);
            }

            if (!Files.isDirectory(baseDir)) {
                return Map.of("error", "Search path is not a directory: " + basePath);
            }

            final SearchState searchState = new SearchState(maxMatches);
            Files.walkFileTree(baseDir, new SearchVisitor(baseDir, regexPattern, searchState, includePattern));

            final List<Map<String, Object>> resultsJson =
                    searchState.matches().stream().map(JsonUtils::toMap).toList();
            final boolean truncated = searchState.isTruncated();

            LOG.info(
                    "Grep search: pattern='{}' in {} files, found {} matches (limit: {})",
                    pattern,
                    searchState.filesSearched(),
                    resultsJson.size(),
                    maxMatches);

            final Map<String, Object> response = new HashMap<>();
            response.put("matches", resultsJson);
            response.put("pattern", pattern);
            response.put("search_path", basePath);
            response.put("files_searched", searchState.filesSearched());
            response.put("total_matches", searchState.totalMatches());
            response.put("matches_returned", resultsJson.size());
            response.put("truncated", truncated);

            if (includePattern != null) {
                response.put("include_pattern", includePattern);
            }

            if (truncated) {
                response.put("limit", maxMatches);
                response.put(
                        "message",
                        "Results truncated at "
                                + maxMatches
                                + " matches. Use a more specific pattern or increase limit.");
            }

            return response;

        } catch (PatternSyntaxException exception) {
            LOG.error("Invalid regex pattern: {}", pattern, exception);
            return Map.of("error", "Invalid regex pattern: " + exception.getDescription());
        } catch (IOException exception) {
            LOG.error("Failed to search files: {}", basePath, exception);
            return Map.of("error", "Failed to search files: " + exception.getMessage());
        }
    }

    private static final class SearchVisitor extends SimpleFileVisitor<Path> {
        private final Path baseDir;
        private final Pattern regexPattern;
        private final SearchState searchState;
        private final Predicate<Path> patternMatches;

        private SearchVisitor(
                final Path baseDir,
                final Pattern regexPattern,
                final SearchState searchState,
                final String includePattern) {
            this.baseDir = baseDir;
            this.regexPattern = regexPattern;
            this.searchState = searchState;
            this.patternMatches = creatMatcher(baseDir, includePattern);
        }

        @Override
        public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
            if (searchState.shouldStop()) {
                return FileVisitResult.TERMINATE;
            }
            if (!dir.equals(baseDir) && isHiddenPath(dir)) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
            if (searchState.shouldStop()) {
                return FileVisitResult.TERMINATE;
            }
            if (!attrs.isRegularFile() || isHiddenPath(file)) {
                return FileVisitResult.CONTINUE;
            }

            final Path relativePath = baseDir.relativize(file);
            if (!patternMatches.test(relativePath)) {
                return FileVisitResult.CONTINUE;
            }

            searchState.incrementFilesSearched();
            try {
                try (BufferedReader reader = Files.newBufferedReader(file)) {
                    String line;
                    int lineNumber = 0;

                    while ((line = reader.readLine()) != null && !searchState.shouldStop()) {
                        lineNumber++;
                        final Matcher matcher = regexPattern.matcher(line);

                        if (matcher.find()) {
                            final Path fileRelativePath = baseDir.relativize(file);
                            final String truncatedLine = truncate(line, MAX_LINE_LENGTH);
                            searchState.recordMatch(new MatchResult(
                                    fileRelativePath.toString(), lineNumber, truncatedLine, matcher.group()));
                        }
                    }
                }
            } catch (IOException exception) {
                LOG.debug("Skipping unreadable file during grep search: {}", file, exception);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(final Path file, final IOException exc) {
            LOG.debug("Skipping file that could not be visited during grep search: {}", file, exc);
            return searchState.shouldStop() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
        }

        private static boolean isHiddenPath(Path path) {
            final Path fileName = path.getFileName();
            if (fileName == null) {
                return false;
            }
            final String name = fileName.toString();
            return name.startsWith(".") || name.startsWith("_");
        }

        private static Predicate<Path> creatMatcher(final Path baseDir, final String includePattern) {
            if (includePattern == null || includePattern.isBlank()) {
                return relativePath -> true;
            }

            final String separator = baseDir.getFileSystem().getSeparator();
            final String normalizedPattern =
                    includePattern.replace("\\", separator).replace("/", separator);
            final PathMatcher pathMatcher = baseDir.getFileSystem().getPathMatcher("glob:" + normalizedPattern);
            final boolean fileNameOnly = !normalizedPattern.contains(separator);

            if (!fileNameOnly) {
                return pathMatcher::matches;
            }

            return relativePath -> pathMatcher.matches(relativePath)
                    || (relativePath.getFileName() != null && pathMatcher.matches(relativePath.getFileName()));
        }
    }

    private static final class SearchState {
        private final int maxMatches;
        private final ArrayList<MatchResult> matches;
        private int filesSearched;
        private int totalMatches;

        private SearchState(final int maxMatches) {
            this.maxMatches = maxMatches;
            this.matches = new ArrayList<>(Math.min(maxMatches, 128));
        }

        private void incrementFilesSearched() {
            filesSearched++;
        }

        private void recordMatch(final MatchResult match) {
            totalMatches++;
            if (matches.size() < maxMatches) {
                matches.add(match);
            }
        }

        private boolean shouldStop() {
            return totalMatches > maxMatches;
        }

        private boolean isTruncated() {
            return totalMatches > maxMatches;
        }

        private int filesSearched() {
            return filesSearched;
        }

        private int totalMatches() {
            return totalMatches;
        }

        private List<MatchResult> matches() {
            return matches;
        }
    }

    private record MatchResult(String file, int lineNumber, String line, String matchedText) {}
}
