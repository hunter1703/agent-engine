package com.agentengine.interfaces.rest;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.service.CloudStorageService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/v1/storage")
@Tag(name = "Storage", description = "Object storage upload API")
public class StorageRestAPI {

  private final CloudStorageService cloudStorageService;

  @Inject
  public StorageRestAPI(final CloudStorageService cloudStorageService) {
    this.cloudStorageService = cloudStorageService;
  }

  @POST
  @Path("/upload")
  @Produces(APPLICATION_JSON)
  @RunOnVirtualThread
  @Operation(summary = "Upload an object to cloud storage")
  @APIResponse(
      responseCode = "200",
      description = "Object uploaded successfully",
      content =
          @Content(
              mediaType = APPLICATION_JSON,
              schema = @Schema(implementation = FileDetails.class)))
  public FileDetails upload(
      @QueryParam("name") final String name,
      @HeaderParam(HttpHeaders.CONTENT_TYPE) final String contentType,
      @HeaderParam(HttpHeaders.CONTENT_LENGTH) final long contentLength,
      final InputStream body) {
    return cloudStorageService.upload(name, body, contentLength, contentType);
  }

  @POST
  @Path("/download")
  @RunOnVirtualThread
  @Operation(summary = "Downloads an object from cloud storage")
  @APIResponse(responseCode = "200", description = "Object downloaded successfully")
  public Response download(final FileDetails fileDetails) {
    final String source = fileDetails.source();
    final int sep = source.indexOf('/');
    final String key = sep >= 0 ? source.substring(sep + 1) : source;
    final CloudStorageService.Content content = cloudStorageService.download(key);
    return Response.ok(content.stream()).type(content.mimeType()).build();
  }
}
