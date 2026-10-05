package com.agentengine.knowledge.api.services;

import com.agentengine.tenancy.AssetAclService;
import com.agentengine.util.ms.client.MicroService;

@MicroService("knowledge")
public interface KnowledgeAclService extends AssetAclService {}
