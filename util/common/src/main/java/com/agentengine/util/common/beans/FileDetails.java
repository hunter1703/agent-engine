package com.agentengine.util.common.beans;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Generic file reference that is storage-backend agnostic.
 *
 * <p>For {@link StorageType#CLOUDSTORAGE}, {@code source} is {@code <bucket>/<key>}.
 */
public record FileDetails(
    String name,
    String source,
    StorageType type,
    String mimeType,
    long size,
    @JsonIgnore InputStream downloaded) {

  /** The MIME type of a file whose type is not known. */
  public static final String UNKNOWN_MIME_TYPE = "application/octet-stream";

  public FileDetails(String name, String source, StorageType type, String mimeType, long size) {
    this(name, source, type, mimeType, size, null);
  }

  /** Two references are equal when they name the same file: the same type and source. */
  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }
    return other instanceof FileDetails that
        && type == that.type
        && Objects.equals(source, that.source);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, source);
  }

  public enum StorageType {
    CLOUDSTORAGE,
    URL,
    LOCAL,
    UNKNOWN;

    public static StorageType valueOfOrDefault(final String value) {
      if (value == null) return UNKNOWN;
      try {
        return valueOf(value.toUpperCase());
      } catch (IllegalArgumentException e) {
        return UNKNOWN;
      }
    }
  }

  public void dispose() {
    if (downloaded != null) {
      try {
        downloaded.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static FileDetails fromUrl(final String url) {
    final String name = url.substring(url.lastIndexOf('/') + 1);
    return new FileDetails(name, url, StorageType.URL, UNKNOWN_MIME_TYPE, -1L);
  }
}
