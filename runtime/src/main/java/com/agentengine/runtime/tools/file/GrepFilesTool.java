package com.agentengine.runtime.tools.file;

import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.plugin.annotations.DiscoverableTool;
import com.agentengine.engine.plugin.annotations.ToolSchema;
import com.agentengine.engine.api.tools.ToolRiskLevel;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tool for searching file contents using regex patterns. Similar to
 * grep/ripgrep with configurable limits and file filtering.
 */
@DiscoverableTool
public final class GrepFilesTool extends BaseFileTool {
  private static final Logger LOG = LoggerFactory.getLogger(GrepFilesTool.class);
  private static final String TOOL_NAME = "grep_files";
  private static final int DEFAULT_LIMIT = 100;
  private static final int MAX_LIMIT = 2000;
  private static final int MAX_LINE_LENGTH = 500;
  private static final int CONTEXT_LINES = 2;

  public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_NAME,
      "Search for text patterns in files using regex. Returns matching lines with file paths and line numbers. "
          + "Supports include patterns for file filtering and context lines around matches.",
      Map.of(), ToolRiskLevel.LOW);

  public GrepFilesTool() {
    super(DESCRIPTOR);
  }

  public Map<String, Object> execute(
      @ToolSchema(name = "pattern", description = "Regex pattern to search for", optional = false) String pattern,
      @ToolSchema(name = "path", description = "Directory path to search in (default: current directory)", optional = true) String searchPath,
      @ToolSchema(name = "include", description = "Glob pattern for files to include (e.g., '*.java', '*.md')", optional = true) String includePattern,
      @ToolSchema(name = "limit", description = "Maximum number of matches to return (default: 100, max: 2000)", optional = true) Integer limit,
      @ToolSchema(name = "case_sensitive", description = "Whether search is case-sensitive (default: false)", optional = true) Boolean caseSensitive) {

    if (pattern == null || pattern.isBlank()) {
      return Map.of("error", "pattern is required");
    }

    int maxMatches = limit != null ? Math.max(1, Math.min(limit, MAX_LIMIT)) : DEFAULT_LIMIT;
    boolean isCaseSensitive = caseSensitive != null && caseSensitive;
    String basePath = searchPath != null ? searchPath : System.getProperty("user.dir", ".");

    try {
      Pattern regexPattern = Pattern.compile(pattern, isCaseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
      Path baseDir = Paths.get(basePath).normalize();

      if (!Files.exists(baseDir)) {
        return Map.of("error", "Search path not found: " + basePath);
      }

      if (!Files.isDirectory(baseDir)) {
        return Map.of("error", "Search path is not a directory: " + basePath);
      }

      List<MatchResult> allMatches = new ArrayList<>();
      int filesSearched = 0;

      try (Stream<Path> paths = Files.walk(baseDir)) {
        List<Path> files = paths.filter(Files::isRegularFile).filter(path -> !isHiddenFile(path))
            .filter(path -> includePattern == null || matchesGlob(path, includePattern)).collect(Collectors.toList());

        for (Path file : files) {
          if (allMatches.size() >= maxMatches) {
            break;
          }

          filesSearched++;
          allMatches.addAll(searchFile(file, baseDir, regexPattern, maxMatches - allMatches.size()));
        }
      }

      List<Map<String, Object>> resultsJson = allMatches.stream().limit(maxMatches).map(this::matchToMap).collect(Collectors.toList());

      boolean truncated = allMatches.size() > maxMatches;

      LOG.info("Grep search: pattern='{}' in {} files, found {} matches (limit: {})", pattern, filesSearched, resultsJson.size(),
          maxMatches);

      Map<String, Object> response = new HashMap<>();
      response.put("matches", resultsJson);
      response.put("pattern", pattern);
      response.put("search_path", basePath);
      response.put("files_searched", filesSearched);
      response.put("total_matches", allMatches.size());
      response.put("matches_returned", resultsJson.size());
      response.put("truncated", truncated);

      if (includePattern != null) {
        response.put("include_pattern", includePattern);
      }

      if (truncated) {
        response.put("limit", maxMatches);
        response.put("message", "Results truncated at " + maxMatches + " matches. Use a more specific pattern or increase limit.");
      }

      return response;

    } catch (PatternSyntaxException e) {
      LOG.error("Invalid regex pattern: {}", pattern, e);
      return Map.of("error", "Invalid regex pattern: " + e.getDescription());
    } catch (IOException e) {
      LOG.error("Failed to search files: {}", basePath, e);
      return Map.of("error", "Failed to search files: " + e.getMessage());
    }
  }

  private List<MatchResult> searchFile(Path file, Path baseDir, Pattern pattern, int remainingLimit) throws IOException {
    List<MatchResult> matches = new ArrayList<>();

    try (BufferedReader reader = Files.newBufferedReader(file)) {
      String line;
      int lineNumber = 0;

      while ((line = reader.readLine()) != null && matches.size() < remainingLimit) {
        lineNumber++;
        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
          Path relativePath = baseDir.relativize(file);
          String truncatedLine = truncate(line, MAX_LINE_LENGTH);

          matches.add(new MatchResult(relativePath.toString(), lineNumber, truncatedLine, matcher.group()));
        }
      }
    }

    return matches;
  }

  private boolean isHiddenFile(Path path) {
    String fileName = path.getFileName().toString();
    return fileName.startsWith(".") || fileName.startsWith("_");
  }

  private boolean matchesGlob(Path path, String globPattern) {
    String fileName = path.getFileName().toString();

    // Simple glob pattern matching (supports * and **)
    String regex = globPattern.replace(".", "\\.").replace("**", ".*").replace("*", "[^/]*");

    return fileName.matches(regex);
  }

  private Map<String, Object> matchToMap(MatchResult match) {
    Map<String, Object> map = new HashMap<>();
    map.put("file", match.file());
    map.put("line_number", match.lineNumber());
    map.put("line", match.line());
    map.put("match", match.matchedText());
    return map;
  }

  private record MatchResult(String file, int lineNumber, String line, String matchedText) {
  }
}
