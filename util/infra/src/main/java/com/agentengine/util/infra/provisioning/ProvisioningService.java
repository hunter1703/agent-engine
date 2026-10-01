package com.agentengine.util.infra.provisioning;

public interface ProvisioningService {

  ProvisioningResult provisionEnvironment(ProvisioningRequest request);

  ProvisioningResult provision(ProvisioningRequest request);
}
