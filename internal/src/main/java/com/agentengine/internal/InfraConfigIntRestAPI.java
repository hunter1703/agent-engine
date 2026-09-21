package com.agentengine.internal;

import com.agentengine.util.context.ContextAware;
import com.agentengine.util.infra.InfraConfig;
import com.agentengine.util.infra.InfraConfigService;
import com.agentengine.util.infra.InfraSetup;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@ContextAware
@Path("/infra-config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InfraConfigIntRestAPI {

  private final InfraConfigService infraConfigService;
  private final Instance<InfraSetup<?>> setups;

  @Inject
  public InfraConfigIntRestAPI(
      final InfraConfigService infraConfigService, final Instance<InfraSetup<?>> setups) {
    this.infraConfigService = infraConfigService;
    this.setups = setups;
  }

  @POST
  public List<InfraConfig> save(final List<InfraConfig> configs) {
    return configs.stream().map(this::setupAndSave).toList();
  }

  private InfraConfig setupAndSave(final InfraConfig config) {
    setups.forEach(setup -> setup.setupIfMatches(config));
    return infraConfigService.save(config);
  }
}
