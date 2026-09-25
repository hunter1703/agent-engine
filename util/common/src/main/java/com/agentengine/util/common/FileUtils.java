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

  private static final Set<String> PDF_MIME_TYPES = Set.of("application/pdf");
  private static final Set<String> PDF_EXTENSIONS = Set.of(".pdf");

  private static final Set<String> OFFICE_MIME_TYPES =
      Set.of(
          "application/msword",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "application/vnd.ms-powerpoint",
          "application/vnd.openxmlformats-officedocument.presentationml.presentation",
          "application/vnd.ms-excel",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
  private static final Set<String> OFFICE_EXTENSIONS =
      Set.of(".doc", ".docx", ".ppt", ".pptx", ".xls", ".xlsx");

  // Raster formats only — SVG is text/XML (already covered by isTextFile's "+xml" suffix match)
  // and doesn't need a vision model to read.
  private static final Set<String> IMAGE_MIME_TYPES =
      Set.of(
          "image/jpeg",
          "image/png",
          "image/gif",
          "image/webp",
          "image/bmp",
          "image/tiff",
          "image/heic",
          "image/heif");
  private static final Set<String> IMAGE_EXTENSIONS =
      Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".tiff", ".tif", ".heic", ".heif");

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

  public static boolean isPdfFile(final FileDetails fileDetails) {
    return matches(fileDetails, PDF_MIME_TYPES, PDF_EXTENSIONS);
  }

  /** Whether {@code fileDetails} is a Word, PowerPoint, or Excel document (old or OOXML format). */
  public static boolean isOfficeFile(final FileDetails fileDetails) {
    return matches(fileDetails, OFFICE_MIME_TYPES, OFFICE_EXTENSIONS);
  }

  public static boolean isImageFile(final FileDetails fileDetails) {
    return matches(fileDetails, IMAGE_MIME_TYPES, IMAGE_EXTENSIONS);
  }

  private static boolean matches(
      final FileDetails fileDetails, final Set<String> mimeTypes, final Set<String> extensions) {
    final String mimeType = fileDetails.mimeType();
    if (mimeType != null && mimeTypes.contains(mimeType.toLowerCase())) {
      return true;
    }
    final String name = fileDetails.name();
    if (name != null) {
      final int dot = name.lastIndexOf('.');
      if (dot >= 0) {
        return extensions.contains(name.substring(dot).toLowerCase());
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
