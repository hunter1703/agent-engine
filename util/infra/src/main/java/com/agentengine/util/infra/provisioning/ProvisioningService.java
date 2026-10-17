package com.agentengine.util.infra.provisioning;

public interface ProvisioningService {

  /**
   * Where this service's {@link #provisionEnvironment} runs relative to every other one, lower
   * first. Services are otherwise provisioned in no particular order, so one whose environment
   * provisioning depends on another's having already run (e.g. a service that invokes the agent
   * service, which needs the agent service's own event-store and microservice registration to exist
   * first) overrides this — on the depended-on service, not the dependent, so the ordering is
   * declared once no matter how many services end up depending on it.
   */
  default int order() {
    return 0;
  }

  ProvisioningResult provisionEnvironment(ProvisioningRequest request);

  ProvisioningResult provision(ProvisioningRequest request);
}
