package com.agentengine.internal;

import com.agentengine.catalog.api.services.AgentService;
import com.agentengine.catalog.api.services.ModelService;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.context.ContextAware;
import com.agentengine.util.models.config.ModelConfig;
import com.agentengine.util.ms.client.MicroServiceClientProvider;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Upserts model and agent configs as the system customer. Served under {@code /internal}, which
 * comes from {@code quarkus.http.root-path} rather than being repeated here. The equivalent {@code
 * /v1/model/upsert} and {@code /v1/agent/upsert} endpoints run as whichever tenant the caller is
 * logged in as, so they can never seed a config meant to be shared across every customer (e.g. a
 * model or agent a system-triggered job invokes); this endpoint exists for that case, relying on
 * {@link InternalContextFilter} already running every request under this path as the system
 * customer.
 */
@ContextAware
@Path("/catalog")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CatalogIntRestAPI {

  private final ModelService modelService;
  private final AgentService agentService;

  @Inject
  public CatalogIntRestAPI(final MicroServiceClientProvider provider) {
    this.modelService = provider.getRaw(ModelService.class);
    this.agentService = provider.getRaw(AgentService.class);
  }

  @POST
  @Path("/model/upsert")
  public ModelConfig upsertModel(
      @QueryParam("skipVersion") @DefaultValue("false") final boolean skipVersion,
      final ModelConfig modelConfig) {
    return modelService.saveModel(modelConfig, skipVersion);
  }

  @POST
  @Path("/agent/upsert")
  public BaseAgentConfig upsertAgent(
      @QueryParam("skipVersion") @DefaultValue("false") final boolean skipVersion,
      final BaseAgentConfig agentConfig) {
    return agentService.saveAgent(agentConfig, skipVersion);
  }
}
