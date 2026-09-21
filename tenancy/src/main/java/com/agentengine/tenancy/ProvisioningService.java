package com.agentengine.tenancy;

public interface ProvisioningService {

  ProvisioningResult provisionEnvironment(ProvisioningRequest request);

  ProvisioningResult provision(ProvisioningRequest request);
}
