package com.agentengine.util.common.beans;

import java.util.Objects;

/**
 * Generic file reference that is storage-backend agnostic.
 *
 * <p>For {@link StorageType#CLOUDSTORAGE}, {@code source} is {@code <bucket>/<key>}.
 */
public record FileDetails(
    String name, String source, StorageType type, String mimeType, long size) {

  /** Original filename including extension, e.g. {@code photo.jpg}. */
  @Override
  public String name() {
    return name;
  }

  @Override
  public String source() {
    return source;
  }

  @Override
  public StorageType type() {
    return type;
  }

  /** Optional MIME type, e.g. {@code "image/jpeg"}. */
  @Override
  public String mimeType() {
    return mimeType;
  }

  /** File size in bytes; {@code -1} if unknown. */
  @Override
  public long size() {
    return size;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FileDetails that = (FileDetails) o;
    return Objects.equals(source, that.source);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(source);
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

  public static FileDetails fromUrl(final String url) {
    final String name = url.substring(url.lastIndexOf('/') + 1);
    return new FileDetails(name, url, StorageType.URL, "application/image", -1L);
  }
}
