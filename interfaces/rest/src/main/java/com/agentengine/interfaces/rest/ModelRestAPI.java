package com.agentengine.interfaces.rest;

import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.services.ModelService;
import com.agentengine.util.common.StringUtils;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Model", description = "Model Management APIs")
@RunOnVirtualThread
public class ModelRestAPI {

  private final ModelService modelService;

  @Inject
  public ModelRestAPI(final ModelService modelService) {
    this.modelService = modelService;
  }

  @GET
  @Path("/model/{modelId}")
  @Operation(summary = "Get a model", description = "Retrieves a specific model configuration by ID.")
  @APIResponse(responseCode = "200", description = "Model configuration found", content = @Content(schema = @Schema(implementation = ModelConfig.class)))
  @APIResponse(responseCode = "404", description = "Model not found")
  public ModelConfig getModel(@PathParam("modelId") final String modelId) {
    if (StringUtils.isBlank(modelId)) {
      throw new WebApplicationException("Model ID is required", 400);
    }

    return modelService.getModel(modelId).orElseThrow(() -> new WebApplicationException("Model not found", 404));
  }

  @POST
  @Path("/model")
  @Operation(summary = "Create a model", description = "Creates a new model configuration.")
  @APIResponse(responseCode = "201", description = "Model created", content = @Content(schema = @Schema(implementation = ModelConfig.class)))
  @APIResponse(responseCode = "409", description = "Model already exists")
  public Response createModel(final ModelConfig modelConfig) {
    if (modelConfig == null) {
      throw new WebApplicationException("Model config is required", 400);
    }
    return Response.status(Response.Status.CREATED).entity(modelService.createModel(modelConfig)).build();
  }

  @POST
  @Path("/model/upsert")
  @Operation(summary = "Upsert a model", description = "Creates a new model configuration or updates an existing one.")
  @APIResponse(responseCode = "200", description = "Model created or updated", content = @Content(schema = @Schema(implementation = ModelConfig.class)))
  public ModelConfig upsertModel(final ModelConfig modelConfig) {
    if (modelConfig == null) {
      throw new WebApplicationException("Model config is required", 400);
    }
    return modelService.saveModel(modelConfig);
  }

  @PUT
  @Path("/model/{modelId}")
  @Operation(summary = "Update a model", description = "Updates an existing model configuration.")
  @APIResponse(responseCode = "200", description = "Model updated", content = @Content(schema = @Schema(implementation = ModelConfig.class)))
  @APIResponse(responseCode = "404", description = "Model not found")
  public ModelConfig updateModel(@PathParam("modelId") final String modelId, final ModelConfig modelConfig) {
    if (modelConfig == null) {
      throw new WebApplicationException("Model config is required", 400);
    }
    if (StringUtils.isBlank(modelId)) {
      throw new WebApplicationException("Model ID is required", 400);
    }
    if (StringUtils.isNotBlank(modelConfig.getId()) && !modelId.equals(modelConfig.getId())) {
      throw new WebApplicationException("Path modelId must match payload id", 400);
    }
    return modelService.updateModel(modelId, modelConfig);
  }

  @DELETE
  @Path("/model/{modelId}")
  @Operation(summary = "Delete a model", description = "Deletes an existing model configuration.")
  @APIResponse(responseCode = "204", description = "Model deleted")
  @APIResponse(responseCode = "404", description = "Model not found")
  public void deleteModel(@PathParam("modelId") final String modelId) {
    if (StringUtils.isBlank(modelId)) {
      throw new WebApplicationException("Model ID is required", 400);
    }

    if (!modelService.deleteModel(modelId)) {
      throw new WebApplicationException("Model not found", 404);
    }
  }
}
