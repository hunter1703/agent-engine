package com.agentengine.internal;

import com.agentengine.connectors.api.beans.Connection;
import com.agentengine.connectors.api.services.ConnectionService;
import com.agentengine.util.context.ContextAware;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Upserts connections as the system customer. Served under {@code /internal}, which comes from
 * {@code quarkus.http.root-path} rather than being repeated here. The equivalent {@code
 * /v1/connection} endpoint runs as whichever tenant the caller is logged in as, so it can never
 * seed a connection meant to be shared across every customer (e.g. one a system-triggered agent's
 * tool depends on); this endpoint exists for that case, relying on {@link InternalContextFilter}
 * already running every request under this path as the system customer.
 */
@ContextAware
@Path("/connectors")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConnectorsIntRestAPI {

  private final ConnectionService connectionService;

  @Inject
  public ConnectorsIntRestAPI(final ConnectionService connectionService) {
    this.connectionService = connectionService;
  }

  @POST
  @Path("/connection/upsert")
  public Connection upsertConnection(
      @QueryParam("skipVersion") @DefaultValue("false") final boolean skipVersion,
      final Connection connection) {
    return connectionService.saveConnection(connection, skipVersion);
  }
}
