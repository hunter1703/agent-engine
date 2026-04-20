package com.agentengine.interfaces.rest;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import com.agentengine.util.cloudstorage.CloudStorageService;
import com.agentengine.util.cloudstorage.StoredObject;
import com.agentengine.util.cloudstorage.localstack.LocalStackInfraConfig;
import com.agentengine.util.mongodb.infra.InfraConfigService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import java.io.InputStream;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST API for uploading objects to cloud storage.
 *
 * <p>Accepts a raw byte stream and stores it under the given key in the default bucket.
 * Returns the stored object metadata (bucket, key, size, media type, etag).
 */
@Path("/v1/storage")
@Tag(name = "Storage", description = "Object storage upload API")
public class StorageRestAPI {

    private final CloudStorageService cloudStorageService;
    private final InfraConfigService infraConfigService;

    @Inject
    public StorageRestAPI(
            final CloudStorageService cloudStorageService,
            final InfraConfigService infraConfigService) {
        this.cloudStorageService = cloudStorageService;
        this.infraConfigService = infraConfigService;
    }

    /**
     * Uploads a raw byte stream to the default bucket.
     *
     * <p>The object key is taken from the URL path. Supply {@code Content-Type} and
     * {@code Content-Length} headers for accurate metadata storage.
     *
     * <p>Example:
     * <pre>
     *   POST /v1/storage/objects/photos/sunset.jpg
     *   Content-Type: image/jpeg
     *   Content-Length: 204800
     *   &lt;binary body&gt;
     * </pre>
     *
     * @param key           object key (path within the bucket, may contain slashes)
     * @param contentType   MIME type of the uploaded file
     * @param contentLength byte length of the body; -1 if unknown
     * @param body          raw byte stream
     * @return metadata of the stored object
     */
    @POST
    @Path("/objects/{key: .+}")
    @Consumes("application/octet-stream")
    @Produces(APPLICATION_JSON)
    @RunOnVirtualThread
    @Operation(summary = "Upload an object to cloud storage")
    @APIResponse(
            responseCode = "200",
            description = "Object uploaded successfully",
            content = @Content(
                    mediaType = APPLICATION_JSON,
                    schema = @Schema(implementation = StoredObject.class)))
    @APIResponse(responseCode = "400", description = "Missing or invalid Content-Type")
    public StoredObject upload(
            @PathParam("key") final String key,
            @HeaderParam(HttpHeaders.CONTENT_TYPE) final String contentType,
            @HeaderParam(HttpHeaders.CONTENT_LENGTH) final long contentLength,
            final InputStream body) {

        final LocalStackInfraConfig config = infraConfigService.findById(
                LocalStackInfraConfig.CATEGORY, LocalStackInfraConfig.CONFIG_ID);
        final String bucket = config != null ? config.getDefaultBucket() : "agent-assets";
        final String mediaType = contentType != null ? contentType : "application/octet-stream";

        return cloudStorageService.upload(bucket, key, body, contentLength, mediaType);
    }
}
