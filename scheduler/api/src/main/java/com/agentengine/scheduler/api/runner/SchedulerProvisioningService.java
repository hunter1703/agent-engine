package com.agentengine.scheduler.api.runner;

import com.agentengine.util.ms.client.MicroService;
import com.agentengine.tenancy.ProvisioningService;

@MicroService("scheduler")
public interface SchedulerProvisioningService extends ProvisioningService {}
