package com.agentengine.util.common;

import com.agentengine.util.common.beans.FileDetails;
import java.util.Set;

public final class FileUtils {

  private static final Set<String> TEXT_MIME_TYPES =
      Set.of(
          "application/json",
          "application/xml",
          "application/yaml",
          "application/x-yaml",
          "application/toml",
          "application/sql",
          "application/javascript",
          "application/x-sh",
          "application/x-subrip");
  private static final Set<String> TEXT_EXTENSIONS =
      Set.of(
          ".txt",
          ".md",
          ".json",
          ".yaml",
          ".yml",
          ".csv",
          ".tsv",
          ".xml",
          ".log",
          ".properties",
          ".srt",
          ".vtt",
          ".sub",
          ".html",
          ".htm",
          ".css",
          ".js",
          ".sql",
          ".sh",
          ".toml",
          ".ini",
          ".conf",
          ".cfg");

  private FileUtils() {}

  public static boolean isTextFile(final FileDetails fileDetails) {
    return isTextFile(fileDetails.mimeType(), fileDetails.name());
  }

  public static boolean isTextFile(final String mimeType, final String name) {
    if (mimeType != null) {
      final String lower = mimeType.toLowerCase();
      if (lower.startsWith("text/")
          || lower.endsWith("+json")
          || lower.endsWith("+xml")
          || TEXT_MIME_TYPES.contains(lower)) {
        return true;
      }
    }
    if (name != null) {
      final int dot = name.lastIndexOf('.');
      if (dot >= 0) {
        return TEXT_EXTENSIONS.contains(name.substring(dot).toLowerCase());
      }
    }
    return false;
  }

  public static String nameFromSource(final String source) {
    final String name = source.substring(source.lastIndexOf('/') + 1);
    return StringUtils.isBlank(name) ? source : name;
  }

  public record BucketKey(String bucket, String key) {
    public static BucketKey parse(final String source, final String defaultBucket) {
      final int index = source.indexOf('/');
      return index >= 0
          ? new BucketKey(source.substring(0, index), source.substring(index + 1))
          : new BucketKey(defaultBucket, source);
    }
  }
}
