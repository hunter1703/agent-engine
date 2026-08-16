package com.agentengine.agent.infra.tools.file;

import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Base class for file operation tools. Provides common functionality like path resolution and
 * content hashing.
 */
public abstract class BaseFileTool extends Tool {

  protected BaseFileTool(final ToolDescriptor toolDescriptor) {
    super(toolDescriptor);
  }

  protected static FileDetails readFile(Path path, long offset, long limit) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      List<String> selectedLines = new ArrayList<>();
      long lineCount = 0;
      long to = offset + limit;
      try (InputStream in = Files.newInputStream(path);
          DigestInputStream din = new DigestInputStream(in, digest);
          BufferedReader reader =
              new BufferedReader(new InputStreamReader(din, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          lineCount++;

          if (lineCount >= offset && lineCount < to) {
            selectedLines.add(line);
          }
        }
      } catch (IOException exception) {
        throw new RuntimeException(exception);
      }

      return new FileDetails(
          lineCount, HexFormat.of().formatHex(digest.digest()), List.copyOf(selectedLines));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 algorithm not available", exception);
    }
  }

  /**
   * Resolves a file path, handling both absolute and relative paths.
   *
   * @param filePath the file path to resolve (can be absolute or relative)
   * @return resolved Path
   */
  protected Path resolvePath(String filePath) {
    Path path = Paths.get(filePath);

    // If absolute path, use as-is
    if (path.isAbsolute()) {
      return path;
    }

    // Use current working directory for relative paths
    String cwd = System.getProperty("user.dir", ".");
    return Paths.get(cwd).resolve(filePath).normalize();
  }

  protected static String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength) + "... [truncated]";
  }

  protected record FileDetails(long numLines, String hash, List<String> content) {}
}
