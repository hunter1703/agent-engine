package com.agentengine.agent.api.model;

import com.agentengine.util.agents.AgentFileDetails;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.Permission;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ResourceGrants(List<AgentFileDetails> knowledges, NotebookGrants notebookGrants) {

  public static final ResourceGrants EMPTY = new ResourceGrants(null, null);

  public ResourceGrants(
      final List<AgentFileDetails> knowledges, final NotebookGrants notebookGrants) {
    this.knowledges = CollectionUtils.nullSafeList(knowledges);
    this.notebookGrants = notebookGrants == null ? new NotebookGrants(Map.of()) : notebookGrants;
  }

  public List<String> indexedKnowledgeIds() {
    return knowledges.stream()
        .map(AgentFileDetails::knowledgeId)
        .filter(StringUtils::isNotBlank)
        .toList();
  }

  public List<String> nonIndexedKnowledgeSources() {
    return knowledges.stream()
        .filter(fileDetails -> StringUtils.isBlank(fileDetails.knowledgeId()))
        .map(AgentFileDetails::source)
        .toList();
  }

  public ResourceGrants merge(final ResourceGrants incoming) {
    if (incoming == null) {
      return this;
    }
    final Set<AgentFileDetails> files = new LinkedHashSet<>(knowledges);
    files.addAll(incoming.knowledges());
    final Map<String, Permission> mergedNotebookGrants =
        new LinkedHashMap<>(notebookGrants.grants());
    mergedNotebookGrants.putAll(incoming.notebookGrants().grants());
    return new ResourceGrants(new ArrayList<>(files), new NotebookGrants(mergedNotebookGrants));
  }

  public boolean isEmpty() {
    return knowledges.isEmpty() && notebookGrants.grants().isEmpty();
  }
}
