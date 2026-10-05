package com.agentengine.catalog.api.services;

import com.agentengine.tenancy.AssetAclService;
import com.agentengine.util.ms.client.MicroService;

@MicroService("catalog")
public interface AgentSessionAclService extends AssetAclService {}
