package com.agentengine.interfaces.rest;

import com.agentengine.interfaces.rest.dto.SchemaLookupRequest;
import com.agentengine.interfaces.rest.handlers.SchemaRequestHandler;
import com.agentengine.interfaces.rest.services.BuilderDefinitionService;
import com.agentengine.util.common.builder.BuilderMode;
import com.agentengine.util.common.exception.AssetNotFoundException;
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
import java.util.Objects;
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
      throw new IllegalArgumentException("Unsupported mode '" + mode + "'. Expected one of: create, edit, view");
    }
    try {
      return Response.ok(definitionService.getDefinition(assetType).resolve(requestedMode)).build();
    } catch (IllegalArgumentException e) {
      throw new AssetNotFoundException("Schema", assetType);
    }
  }

  @POST
  @Path("/")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Object resolveSchema(SchemaLookupRequest request) {
    final String assetType = request.assetType();
    SchemaRequestHandler handler = handlers.get(assetType);
    if (handler != null) {
      return handler.handle(request);
    }
    throw new AssetNotFoundException("Schema", assetType);
  }
}
