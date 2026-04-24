package com.agentengine.knowledge.api.services;

import com.agentengine.knowledge.api.beans.IndexRequest;
import com.agentengine.knowledge.api.beans.IndexingStatus;
import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.beans.KnowledgeSearchResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.ms.MicroService;
import java.util.List;

@MicroService("knowledge")
public interface KnowledgeService {

    Knowledge create(IndexRequest request);

    Knowledge findById(String id);

    List<Knowledge> findByQuery(Query query);

    Knowledge reindex(String id, IndexRequest request);

    boolean deleteById(String id);

    IndexingStatus getStatusById(String id);

    List<KnowledgeSearchResult> search(Query query);
}
