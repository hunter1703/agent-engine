package com.agentengine.tenancy;

import com.agentengine.util.infra.provisioning.ProvisioningService;
import com.agentengine.util.ms.client.MicroService;

@MicroService("tenancy")
public interface TenancyProvisioningService extends ProvisioningService {}
