package com.agentengine.knowledge.api.services;

import com.agentengine.knowledge.api.beans.*;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.ms.client.MicroService;
import java.util.Collection;
import java.util.Map;

@MicroService("knowledge")
public interface KnowledgeService {

  Knowledge create(IndexRequest request);

  Knowledge findById(String id);

  Map<String, Knowledge> findByIds(Collection<String> id);

  PaginatedResult<Knowledge> findByQuery(Query query);

  Knowledge reindex(String id, IndexRequest request);

  boolean deleteById(String id);

  PaginatedResult<KnowledgeChunk> searchInKnowledge(Query query);
}
