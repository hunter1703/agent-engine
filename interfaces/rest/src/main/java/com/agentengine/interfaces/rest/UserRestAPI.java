package com.agentengine.interfaces.rest;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import com.agentengine.tenancy.UserService;
import com.agentengine.tenancy.beans.User;
import com.agentengine.util.context.ContextAware;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/v1/user")
@Tag(name = "User", description = "User management API")
@ContextAware
@RunOnVirtualThread
public class UserRestAPI {

  private final UserService userService;

  @Inject
  public UserRestAPI(final UserService userService) {
    this.userService = userService;
  }

  @GET
  @Path("/{id}")
  @Produces(APPLICATION_JSON)
  @Operation(summary = "Get user by ID")
  public User get(@PathParam("id") final String id) {
    return userService.get(id);
  }

  @POST
  @Path("/")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Operation(summary = "Create user")
  public User create(final User user) {
    return userService.create(user);
  }

  @PUT
  @Path("/{id}")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Operation(summary = "Update user")
  public User update(@PathParam("id") final String id, final User user) {
    return userService.update(id, user);
  }

  @DELETE
  @Path("/{id}")
  @Produces(APPLICATION_JSON)
  @Operation(summary = "Delete user")
  public void delete(@PathParam("id") final String id) {
    userService.delete(id);
  }
}
