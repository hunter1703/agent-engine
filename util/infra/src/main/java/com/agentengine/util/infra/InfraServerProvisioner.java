package com.agentengine.util.infra;

public abstract class InfraServerProvisioner<S extends InfraConfig> {

  private final InfraConfigService infraConfigService;

  protected InfraServerProvisioner(final InfraConfigService infraConfigService) {
    this.infraConfigService = infraConfigService;
  }

  public S provision(final S server) {
    setup(server);
    return infraConfigService.save(server);
  }

  protected void setup(final S server) {}
}
