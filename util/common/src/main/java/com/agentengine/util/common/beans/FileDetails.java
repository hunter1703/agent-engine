package com.agentengine.util.common.beans;

import com.agentengine.util.common.annotations.ToolSchema;
import java.util.Objects;

/**
 * Generic file reference that is storage-backend agnostic.
 *
 * <p>For {@link StorageType#CLOUDSTORAGE}, {@code source} is {@code <bucket>/<key>}.
 */
public record FileDetails(
    String name, String source, StorageType type, String mimeType, long size) {

  public FileDetails(
      @ToolSchema(
              name = "name",
              description =
                  "Display name for the file, e.g. photo.jpg. Optional — leave blank if unknown.",
              optional = true)
          final String name,
      @ToolSchema(
              name = "source",
              description =
                  "The complete storage location of the file. For CLOUDSTORAGE this is the full bucket/key e.g. 'agent-assets/2ec11fea6b814ddc91fc57829890e788'. Do not split, shorten, or modify this value.")
          final String source,
      @ToolSchema(
              name = "type",
              description =
                  "Where the file is stored — NOT the file format. This is not a MIME type.",
              enums = {"CLOUDSTORAGE", "URL", "LOCAL", "UNKNOWN"})
          final StorageType type,
      @ToolSchema(
              name = "mimeType",
              description = "MIME type of the file, e.g. image/jpeg or image/png.",
              optional = true)
          final String mimeType,
      @ToolSchema(
              name = "size",
              description = "File size in bytes. Use -1 if unknown.",
              optional = true)
          final long size) {
    this.name = name;
    this.source = source;
    this.type = type;
    this.mimeType = mimeType;
    this.size = size;
  }

  /** Original filename including extension, e.g. {@code photo.jpg}. */
  @Override
  public String name() {
    return name;
  }

  /** Storage-specific source to the file. */
  @Override
  public String source() {
    return source;
  }

  /** Storage backend type. */
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
