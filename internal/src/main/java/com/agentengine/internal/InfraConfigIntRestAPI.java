package com.agentengine.internal;

import com.agentengine.util.infra.InfraConfig;
import com.agentengine.util.infra.InfraConfigService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/infra-config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InfraConfigIntRestAPI {

  private final InfraConfigService infraConfigService;

  @Inject
  public InfraConfigIntRestAPI(final InfraConfigService infraConfigService) {
    this.infraConfigService = infraConfigService;
  }

  @POST
  public List<InfraConfig> save(final List<InfraConfig> configs) {
    return infraConfigService.saveAll(configs);
  }
}
