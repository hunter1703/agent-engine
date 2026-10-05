package com.agentengine.tenancy.core.helpers;

import com.agentengine.knowledge.api.services.KnowledgeAclService;
import com.agentengine.util.common.beans.AssetClass;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class KnowledgeAclHelper extends AssetAclHelper {

  @Inject
  public KnowledgeAclHelper(final KnowledgeAclService aclService) {
    super(AssetClass.KNOWLEDGE, aclService);
  }
}
