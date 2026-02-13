package com.agentengine.interfaces.rest;

import com.agentengine.interfaces.rest.services.ResourceService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.Map;

@Path("/schemas")
public class SchemaRestAPI {

    @Inject
    ResourceService resourceService;

    @GET
    @Path("/{assetType}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSchema(@PathParam("assetType") String assetType) {
        try {
            return Response.ok(resourceService.getJsonResource(assetType)).build();
        } catch (IOException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(STR."Schema for assetType '\{assetType}' not found: \{e.getMessage()}")
                    .build();
        }
    }
}
