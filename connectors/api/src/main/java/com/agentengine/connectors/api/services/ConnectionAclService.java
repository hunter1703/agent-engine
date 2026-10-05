package com.agentengine.connectors.api.services;

import com.agentengine.tenancy.AssetAclService;
import com.agentengine.util.ms.client.MicroService;

@MicroService("connectors")
public interface ConnectionAclService extends AssetAclService {}
