package com.agentengine.agent.api.model;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.beans.Permission;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ResourceGrants(
    List<String> knowledgeIds, List<FileDetails> knowledgeFiles, NotebookGrants notebookGrants) {

  public static final ResourceGrants EMPTY = new ResourceGrants(null, null, null);

  public ResourceGrants(
      final List<String> knowledgeIds,
      final List<FileDetails> knowledgeFiles,
      final NotebookGrants notebookGrants) {
    this.knowledgeIds = CollectionUtils.nullSafeList(knowledgeIds);
    this.knowledgeFiles = CollectionUtils.nullSafeList(knowledgeFiles);
    this.notebookGrants = notebookGrants == null ? new NotebookGrants(Map.of()) : notebookGrants;
  }

  public List<String> knowledgeSources() {
    return knowledgeFiles.stream().map(FileDetails::source).toList();
  }

  public ResourceGrants merge(final ResourceGrants incoming) {
    if (incoming == null) {
      return this;
    }
    final Set<String> ids = new LinkedHashSet<>(knowledgeIds);
    ids.addAll(incoming.knowledgeIds());
    final Set<FileDetails> files = new LinkedHashSet<>(knowledgeFiles);
    files.addAll(incoming.knowledgeFiles());
    final Map<String, Permission> mergedNotebookGrants =
        new LinkedHashMap<>(notebookGrants.grants());
    mergedNotebookGrants.putAll(incoming.notebookGrants().grants());
    return new ResourceGrants(
        new ArrayList<>(ids), new ArrayList<>(files), new NotebookGrants(mergedNotebookGrants));
  }

  public boolean isEmpty() {
    return knowledgeIds.isEmpty() && knowledgeFiles.isEmpty() && notebookGrants.grants().isEmpty();
  }
}
