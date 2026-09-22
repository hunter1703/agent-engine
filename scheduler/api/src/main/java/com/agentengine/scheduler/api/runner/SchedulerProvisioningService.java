package com.agentengine.scheduler.api.runner;

import com.agentengine.tenancy.ProvisioningService;
import com.agentengine.util.ms.client.MicroService;

@MicroService("scheduler")
public interface SchedulerProvisioningService extends ProvisioningService {}
