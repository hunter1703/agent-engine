package com.agentengine.tenancy;

public interface ProvisioningService {

  ProvisioningResult provisionEnvironment(ProvisioningRequest provisioningRequest);

  ProvisioningResult provision(int customerId, ProvisioningRequest request);
}
