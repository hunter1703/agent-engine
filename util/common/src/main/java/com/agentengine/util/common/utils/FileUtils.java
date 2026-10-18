package com.agentengine.util.common.utils;

import com.agentengine.util.common.beans.FileDetails;
import java.net.URI;
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

  public static final String MARKDOWN_MIME_TYPE = "text/markdown";
  private static final Set<String> MARKDOWN_MIME_TYPES = Set.of(MARKDOWN_MIME_TYPE);

  private static final Set<String> PDF_MIME_TYPES = Set.of("application/pdf");

  private static final Set<String> OFFICE_MIME_TYPES =
      Set.of(
          "application/msword",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "application/vnd.ms-powerpoint",
          "application/vnd.openxmlformats-officedocument.presentationml.presentation",
          "application/vnd.ms-excel",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  // Raster formats only — SVG is text/XML (already covered by isTextFile's "+xml" suffix match)
  // and doesn't need a vision model to read.
  public static final Set<String> IMAGE_MIME_TYPES =
      Set.of(
          "image/jpeg",
          "image/png",
          "image/gif",
          "image/webp",
          "image/bmp",
          "image/tiff",
          "image/heic",
          "image/heif");

  private FileUtils() {}

  public static boolean isTextFile(final FileDetails fileDetails) {
    return fileDetails != null && isTextFile(fileDetails.mimeType());
  }

  public static boolean isTextFile(final String mimeType) {
    if (mimeType == null) {
      return false;
    }
    final String lower = mimeType.toLowerCase();
    return lower.startsWith("text/")
        || lower.endsWith("+json")
        || lower.endsWith("+xml")
        || TEXT_MIME_TYPES.contains(lower);
  }

  public static boolean isMarkdownFile(final FileDetails fileDetails) {
    return matches(fileDetails, MARKDOWN_MIME_TYPES);
  }

  public static boolean isPdfFile(final FileDetails fileDetails) {
    return matches(fileDetails, PDF_MIME_TYPES);
  }

  /** Whether {@code fileDetails} is a Word, PowerPoint, or Excel document (old or OOXML format). */
  public static boolean isOfficeFile(final FileDetails fileDetails) {
    return matches(fileDetails, OFFICE_MIME_TYPES);
  }

  public static boolean isImageFile(final FileDetails fileDetails) {
    return matches(fileDetails, IMAGE_MIME_TYPES);
  }

  private static boolean matches(final FileDetails fileDetails, final Set<String> mimeTypes) {
    if (fileDetails == null || fileDetails.mimeType() == null) {
      return false;
    }
    return mimeTypes.contains(fileDetails.mimeType().toLowerCase());
  }

  public static String nameFromSource(final String source) {
    final String name = source.substring(source.lastIndexOf('/') + 1);
    return StringUtils.isBlank(name) ? source : name;
  }

  /**
   * Derives a filename from a URL or path. If the derived name has no file extension, it inherits
   * the extension from {@code defaultName} if {@code defaultName} has one; if {@code defaultName}
   * has no extension, the derived name is returned with no extension. If no name can be derived
   * from the URL, {@code defaultName} is returned.
   */
  public static String deriveFileName(final String url, final String defaultName) {
    String derivedName = null;
    if (StringUtils.isNotBlank(url)) {
      try {
        final URI uri = URI.create(url);
        final String path = uri.getPath();
        if (StringUtils.isNotBlank(path)) {
          final int lastSlash = path.lastIndexOf('/');
          final String segment =
              lastSlash >= 0 ? path.substring(lastSlash + 1).trim() : path.trim();
          if (StringUtils.isNotBlank(segment)) {
            derivedName = segment;
          }
        }
      } catch (final Exception ignored) {
        final String cleanUrl = url.split("[?#]", 2)[0];
        final int lastSlash = cleanUrl.lastIndexOf('/');
        if (lastSlash >= 0) {
          final String segment = cleanUrl.substring(lastSlash + 1).trim();
          if (StringUtils.isNotBlank(segment)) {
            derivedName = segment;
          }
        }
      }
    }

    if (StringUtils.isBlank(derivedName)) {
      return defaultName != null ? defaultName : "";
    }

    final int derivedDot = derivedName.lastIndexOf('.');
    final boolean hasExtension = derivedDot > 0 && derivedDot < derivedName.length() - 1;
    if (hasExtension) {
      return derivedName;
    }

    if (StringUtils.isNotBlank(defaultName)) {
      final int defaultDot = defaultName.lastIndexOf('.');
      if (defaultDot > 0 && defaultDot < defaultName.length() - 1) {
        return derivedName + defaultName.substring(defaultDot);
      }
    }

    return derivedName;
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
