package com.agentengine.interfaces.rest;

import com.agentengine.knowledge.api.beans.IndexRequest;
import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.context.ContextAware;
import com.agentengine.util.ms.client.MicroServiceClientProvider;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/v1/knowledge")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Knowledge", description = "Knowledge Management APIs")
@RunOnVirtualThread
@ContextAware
public class KnowledgeRestAPI {

  private final KnowledgeService knowledgeService;

  @Inject
  public KnowledgeRestAPI(final MicroServiceClientProvider provider) {
    this.knowledgeService = provider.getRaw(KnowledgeService.class);
  }

  @POST
  @Operation(summary = "Create and index knowledge")
  @APIResponse(
      responseCode = "200",
      description = "Knowledge created and indexed",
      content = @Content(schema = @Schema(implementation = Knowledge.class)))
  @APIResponse(responseCode = "400", description = "Invalid request")
  public Knowledge createKnowledge(final IndexRequest request) {
    if (request == null) {
      throw new WebApplicationException("Index request is required", 400);
    }
    return knowledgeService.index(request);
  }

  @GET
  @Path("/{id}")
  @Operation(summary = "Get knowledge by ID")
  @APIResponse(
      responseCode = "200",
      description = "Knowledge found",
      content = @Content(schema = @Schema(implementation = Knowledge.class)))
  @APIResponse(responseCode = "404", description = "Knowledge not found")
  public Knowledge getKnowledge(@PathParam("id") final String id) {
    if (StringUtils.isBlank(id)) {
      throw new IllegalArgumentException("Knowledge ID is required");
    }
    final Knowledge knowledge = knowledgeService.findById(id);
    if (knowledge == null) {
      throw new AssetNotFoundException(AssetClass.KNOWLEDGE, id);
    }
    return knowledge;
  }

  @DELETE
  @Path("/{id}")
  @Operation(summary = "Delete knowledge by ID")
  @APIResponse(responseCode = "204", description = "Knowledge deleted")
  public void deleteKnowledge(@PathParam("id") final String id) {
    if (StringUtils.isBlank(id)) {
      throw new IllegalArgumentException("Knowledge ID is required");
    }
    knowledgeService.deleteById(id);
  }
}
