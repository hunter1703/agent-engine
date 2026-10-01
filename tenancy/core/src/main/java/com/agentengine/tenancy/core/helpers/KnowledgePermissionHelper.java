package com.agentengine.tenancy.core.helpers;

import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.util.common.beans.AssetClass;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class KnowledgePermissionHelper extends AssetPermissionHelper {

  @Inject
  public KnowledgePermissionHelper(final KnowledgeService knowledgeService) {
    super(AssetClass.KNOWLEDGE, knowledgeService);
  }
}
