package com.agentengine.interfaces.rest;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import com.agentengine.connectors.api.beans.Connection;
import com.agentengine.connectors.api.services.ConnectionService;
import com.agentengine.interfaces.rest.filter.ContextAware;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/v1/connection")
@Tag(name = "Connection", description = "Connection management API")
@ContextAware
@RunOnVirtualThread
public class ConnectionRestAPI {

  private final ConnectionService connectionService;

  @Inject
  public ConnectionRestAPI(final ConnectionService connectionService) {
    this.connectionService = connectionService;
  }

  @POST
  @Path("/")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Operation(summary = "Save a connection")
  public Connection save(final Connection connection) {
    return connectionService.saveConnection(connection);
  }

  @GET
  @Path("/{id}")
  @Produces(APPLICATION_JSON)
  @Operation(summary = "Get a connection by ID")
  public Connection get(@PathParam("id") final String id) {
    return connectionService.getConnection(id);
  }

  @GET
  @Produces(APPLICATION_JSON)
  @Operation(summary = "Get connections by app name")
  public PaginatedResult<Connection> getByAppName(
      @QueryParam("appName") final String appName,
      @QueryParam("pageNumber") @DefaultValue("0") final int pageNumber,
      @QueryParam("pageSize") @DefaultValue("20") final int pageSize) {
    return connectionService.getConnections(appName, new Page(pageNumber, pageSize));
  }
}
