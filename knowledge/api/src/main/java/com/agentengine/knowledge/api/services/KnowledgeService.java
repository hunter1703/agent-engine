package com.agentengine.knowledge.api.services;

import com.agentengine.knowledge.api.beans.*;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.ms.client.MicroService;
import com.agentengine.util.tenancy.Permission;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@MicroService("knowledge")
public interface KnowledgeService {
  /**
   * Creates the knowledge the request describes, or replaces the knowledge that has the request's
   * {@code knowledgeId}, and indexes it: while the call waits when the request says so, else as a
   * scheduled job. A request that skips indexing leaves the knowledge unindexed, with no chunks.
   */
  Knowledge index(IndexRequest request);

  Knowledge findById(String id);

  Map<String, Knowledge> findByIds(Collection<String> id);

  PaginatedResult<Knowledge> findByQuery(Query query);

  /**
   * Runs the indexing pipeline for an already-existing knowledge record — the work {@link #index}
   * schedules asynchronously, and what {@code KnowledgeIndexingJob} calls when the scheduler fires
   * it.
   */
  void runIndexing(String knowledgeId);

  boolean deleteById(String id);

  Set<String> findPermittedIds(Collection<String> ids, Permission permission);

  /**
   * The similarity of each tag of the chunks {@code query}, which has one semantic filter, matches
   * among the knowledge the caller may read, closest first: the mean similarity of the tag's
   * {@code closestChunksPerTag} closest chunks, for at most {@code maxTags} tags.
   */
  Map<String, Double> findTagSimilarities(Query query, int maxTags, int closestChunksPerTag);

  /** The chunks {@code query} matches among the knowledge the caller may read. */
  PaginatedResult<KnowledgeChunk> search(Query query);
}
