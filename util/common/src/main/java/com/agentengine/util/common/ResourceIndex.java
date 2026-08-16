package com.agentengine.util.common;

import java.util.List;
import java.util.Optional;

/**
 * Represents an index of classpath resources, parsed from a simple text file (one filename per
 * line). This avoids the need for expensive and unreliable runtime classpath scanning.
 */
public final class ResourceIndex {
  private final String indexPath;
  private final String directoryPrefix;
  private final LazyLoader<List<String>> entries;

  public ResourceIndex(final String indexPath) {
    this.indexPath = indexPath.startsWith("/") ? indexPath : "/" + indexPath;
    final int lastSlash = this.indexPath.lastIndexOf('/');
    this.directoryPrefix = this.indexPath.substring(0, lastSlash + 1);
    this.entries = new LazyLoader<>(this::loadEntries);
  }

  /** Returns the list of filenames defined in the index. */
  public List<String> listEntries() {
    return entries.get();
  }

  /** Finds and loads the content of a specific file if it is declared in the index. */
  public Optional<String> findContent(final String entryName) {
    if (!entries.get().contains(entryName)) {
      return Optional.empty();
    }
    final String content = ResourceUtils.loadResourceAsString(directoryPrefix + entryName);
    return StringUtils.isBlank(content) ? Optional.empty() : Optional.of(content);
  }

  private List<String> loadEntries() {
    final String indexContent = ResourceUtils.loadResourceAsString(indexPath);
    if (StringUtils.isBlank(indexContent)) {
      return List.of();
    }
    return indexContent
        .lines()
        .map(String::trim)
        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
        .toList();
  }
}
