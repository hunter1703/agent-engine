package com.agentengine.interfaces.rest;

import com.agentengine.interfaces.rest.handlers.SchemaRequestHandler;
import com.agentengine.interfaces.rest.requests.SchemaLookupRequest;
import com.agentengine.interfaces.rest.services.ResourceService;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Path("/schemas")
public class SchemaRestAPI {

  private final ResourceService resourceService;
  private final Map<String, SchemaRequestHandler> handlers;

  @Inject
  public SchemaRestAPI(ResourceService resourceService, Instance<SchemaRequestHandler> handlers) {
    this.resourceService = resourceService;
    this.handlers =
        handlers.stream()
            .collect(Collectors.toMap(SchemaRequestHandler::getAssetType, Function.identity()));
  }

  @GET
  @Path("/{assetType}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getSchema(@PathParam("assetType") String assetType) {
    try {
      return Response.ok(resourceService.getJsonResource(assetType)).build();
    } catch (IOException e) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity("Schema for assetType '" + assetType + "' not found: " + e.getMessage())
          .build();
    }
  }

  @POST
  @Path("/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response resolveSchema(SchemaLookupRequest request) {
    final String assetType = request.assetType();
    SchemaRequestHandler handler = handlers.get(assetType);
    if (handler != null) {
      return Response.ok(handler.handle(request)).build();
    }

    return Response.status(Response.Status.BAD_REQUEST)
        .entity("Unsupported assetType for POST: " + assetType)
        .build();
  }
}
