package com.agentengine.agent.api.model;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.beans.Permission;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ResourceGrants(
    List<String> knowledgeIds, List<String> knowledgeSources, NotebookGrants notebookGrants) {

  public static final ResourceGrants EMPTY = new ResourceGrants(null, null, null);

  public ResourceGrants(
      final List<String> knowledgeIds,
      final List<String> knowledgeSources,
      final NotebookGrants notebookGrants) {
    this.knowledgeIds = CollectionUtils.nullSafeList(knowledgeIds);
    this.knowledgeSources = CollectionUtils.nullSafeList(knowledgeSources);
    this.notebookGrants = notebookGrants == null ? new NotebookGrants(Map.of()) : notebookGrants;
  }

  public ResourceGrants merge(final ResourceGrants incoming) {
    if (incoming == null) {
      return this;
    }
    final Set<String> ids = new LinkedHashSet<>(knowledgeIds);
    ids.addAll(incoming.knowledgeIds());
    final Set<String> sources = new LinkedHashSet<>(knowledgeSources);
    sources.addAll(incoming.knowledgeSources());
    final Map<String, Permission> mergedNotebookGrants =
        new LinkedHashMap<>(notebookGrants.grants());
    mergedNotebookGrants.putAll(incoming.notebookGrants().grants());
    return new ResourceGrants(
        new ArrayList<>(ids), new ArrayList<>(sources), new NotebookGrants(mergedNotebookGrants));
  }

  public boolean isEmpty() {
    return knowledgeIds.isEmpty()
        && knowledgeSources.isEmpty()
        && notebookGrants.grants().isEmpty();
  }
}
