package com.agentengine.agent.api.model;

import com.agentengine.agent.api.annotations.ToolArg;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.beans.FileDetails.StorageType;
import java.util.Objects;

public record AgentFileDetails(
    String name, String source, StorageType type, String mimeType, long size) {

  public AgentFileDetails(
      @ToolArg(
              name = "name",
              description =
                  "Display name for the file, e.g. photo.jpg. Optional — leave blank if unknown.",
              optional = true)
          final String name,
      @ToolArg(
              name = "source",
              description =
                  "The complete storage location of the file. For CLOUDSTORAGE this is the full bucket/key e.g. 'agent-assets/2ec11fea6b814ddc91fc57829890e788'. Do not split, shorten, or modify this value.")
          final String source,
      @ToolArg(
              name = "type",
              description =
                  "Where the file is stored — NOT the file format. This is not a MIME type.",
              enums = {"CLOUDSTORAGE", "URL", "LOCAL", "UNKNOWN"})
          final StorageType type,
      @ToolArg(
              name = "mimeType",
              description = "MIME type of the file, e.g. image/jpeg or image/png.",
              optional = true)
          final String mimeType,
      @ToolArg(
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

  public AgentFileDetails(final FileDetails fileDetails) {
    this(
        fileDetails.name(),
        fileDetails.source(),
        fileDetails.type(),
        fileDetails.mimeType(),
        fileDetails.size());
  }

  public FileDetails toFileDetails() {
    return new FileDetails(name, source, type, mimeType, size);
  }

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

  @Override
  public String mimeType() {
    return mimeType;
  }

  @Override
  public long size() {
    return size;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AgentFileDetails that = (AgentFileDetails) o;
    return Objects.equals(source, that.source);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(source);
  }
}
