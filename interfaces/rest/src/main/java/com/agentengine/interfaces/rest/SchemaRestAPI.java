package com.agentengine.interfaces.rest;

import com.agentengine.interfaces.rest.dto.SchemaLookupRequest;
import com.agentengine.interfaces.rest.handlers.SchemaRequestHandler;
import com.agentengine.interfaces.rest.services.BuilderDefinitionService;
import com.agentengine.util.common.builder.BuilderMode;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Path("/schemas")
@RunOnVirtualThread
public class SchemaRestAPI {

  private final BuilderDefinitionService definitionService;
  private final Map<String, SchemaRequestHandler> handlers;

  @Inject
  public SchemaRestAPI(final BuilderDefinitionService definitionService, final Instance<SchemaRequestHandler> handlers) {
    this.definitionService = definitionService;
    this.handlers = handlers.stream().collect(Collectors.toMap(SchemaRequestHandler::getAssetType, Function.identity()));
  }

  @GET
  @Path("/{assetType}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getSchema(@PathParam("assetType") final String assetType, @QueryParam("mode") final String mode) {
    final BuilderMode requestedMode = BuilderMode.fromString(mode);
    if (mode != null && requestedMode == null) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Unsupported mode '" + mode + "'. Expected one of: create, edit, view")
          .build();
    }
    try {
      return Response.ok(definitionService.getDefinition(assetType).resolve(requestedMode)).build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.NOT_FOUND).entity("Schema for assetType '" + assetType + "' not found: " + e.getMessage())
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

    return Response.status(Response.Status.BAD_REQUEST).entity("Unsupported assetType for POST: " + assetType).build();
  }
}
