package com.agentengine.agent.api.services;

import com.agentengine.util.infra.provisioning.ProvisioningService;
import com.agentengine.util.ms.client.MicroService;

@MicroService("agent")
public interface AgentProvisioningService extends ProvisioningService {}
