package com.agentengine.agent.api.services;

import com.agentengine.tenancy.AssetAclService;
import com.agentengine.util.ms.client.MicroService;

@MicroService("agent")
public interface AgentScheduleAclService extends AssetAclService {}
