package com.agentengine.knowledge.api.services;

import com.agentengine.knowledge.api.beans.*;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.ms.client.MicroService;
import com.agentengine.util.tenancy.Permission;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

@MicroService("knowledge")
public interface KnowledgeService {
  Knowledge create(IndexRequest request);

  Knowledge findById(String id);

  Map<String, Knowledge> findByIds(Collection<String> id);

  PaginatedResult<Knowledge> findByQuery(Query query);

  Knowledge reindex(String id, IndexRequest request);

  /**
   * Runs the indexing pipeline for an already-existing knowledge record — the work {@link #create}
   * and {@link #reindex} schedule asynchronously, and what {@code KnowledgeIndexingJob} calls when
   * the scheduler fires it.
   */
  void runIndexing(String knowledgeId);

  boolean deleteById(String id);

  Set<String> findPermittedIds(Collection<String> ids, Permission permission);

  /**
   * The chunks {@code query} matches within the knowledge {@code knowledgeIds} names, or within all
   * the knowledge the caller may read when it names none; knowledge the caller may not read is left
   * out.
   */
  PaginatedResult<KnowledgeChunk> searchInKnowledge(Collection<String> knowledgeIds, Query query);
}
