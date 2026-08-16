package com.agentengine.knowledge.core.pipeline;

import com.agentengine.knowledge.api.beans.Knowledge;

public interface KnowledgeIndexer {

  boolean canIndex(Knowledge knowledge);

  int index(Knowledge knowledge);

  int priority();
}
