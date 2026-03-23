package com.agentengine.interfaces.rest;

import com.agentengine.interfaces.rest.dto.AssetRequest;
import com.agentengine.interfaces.rest.handlers.catalog.AssetHandler;
import com.agentengine.interfaces.rest.handlers.catalog.NamedAssetHandler;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.beans.NamedEntity;
import com.agentengine.util.common.query.PaginatedResult;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/v1/catalog")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Catalog", description = "Resource catalog APIs")
@RunOnVirtualThread
public class ResourceCatalogAPI {

  private final Map<String, AssetHandler<?>> assetHandlers;

  @Inject
  public ResourceCatalogAPI(Instance<AssetHandler<?>> handlers) {
    this.assetHandlers = handlers.stream().collect(Collectors.toUnmodifiableMap(AssetHandler::getAssetType, Function.identity()));
  }

  @POST
  @Path("/list")
  @Operation(summary = "List resources", description = "List resources of a specific type based on provided criteria.")
  @APIResponse(responseCode = "200", description = "List of resources", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PaginatedResult.class)))
  public PaginatedResult<? extends NamedEntity> listResources(AssetRequest request) {
    if (request == null || request.getAssetType() == null) {
      throw new IllegalArgumentException("Resource type is required");
    }

    AssetHandler<?> rawHandler = assetHandlers.get(request.getAssetType());
    if (rawHandler == null) {
      throw new IllegalArgumentException("Unsupported resource type: " + request.getAssetType());
    }
    if (!(rawHandler instanceof NamedAssetHandler<?> handler)) {
      throw new IllegalArgumentException("Resource type does not support listing: " + request.getAssetType());
    }

    return handler.listAssets(request);
  }

  @POST
  @Path("/search")
  @Operation(summary = "Search resources", description = "Searches resources of a specific type based on provided criteria.")
  @APIResponse(responseCode = "200", description = "List of resources", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PaginatedResult.class)))
  public PaginatedResult<?> searchResources(AssetRequest request) {
    if (request == null || request.getAssetType() == null) {
      throw new IllegalArgumentException("Resource type is required");
    }

    AssetHandler<?> handler = assetHandlers.get(request.getAssetType());
    if (handler == null) {
      throw new IllegalArgumentException("Unsupported resource type: " + request.getAssetType());
    }

    return handler.findAssets(request);
  }

  @GET
  @Path("/{resourceType}/{id}")
  @Operation(summary = "Get resource by ID", description = "Retrieves a specific resource by its ID.")
  @APIResponse(responseCode = "200", description = "Resource details", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Object.class)))
  @APIResponse(responseCode = "404", description = "Resource not found")
  public <T extends BaseEntity> T getResource(@PathParam("resourceType") String resourceType, @PathParam("id") String id,
      @Context UriInfo uriInfo) {
    if (resourceType == null || id == null) {
      throw new IllegalArgumentException("Resource type and ID are required");
    }

    // noinspection unchecked
    AssetHandler<T> handler = (AssetHandler<T>) assetHandlers.get(resourceType);
    if (handler == null) {
      throw new IllegalArgumentException("Unsupported resource type: " + resourceType);
    }

    AssetRequest request = new AssetRequest();
    request.setAssetType(resourceType);
    request.setKeys(List.of(id));

    Map<String, Object> options = new HashMap<>();
    if (uriInfo != null && uriInfo.getQueryParameters() != null) {
      uriInfo.getQueryParameters().forEach((key, values) -> {
        if (values != null && !values.isEmpty()) {
          options.put(key, values.getFirst());
        }
      });
    }
    request.setOptions(options);

    final Map<String, T> assetsByIds = handler.getAssetsByIds(request);
    T resource = assetsByIds.get(id);
    if (resource == null) {
      throw new WebApplicationException("Resource not found: " + id, 404);
    }

    return resource;
  }
}
