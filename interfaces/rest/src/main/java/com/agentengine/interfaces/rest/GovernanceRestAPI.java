package com.agentengine.interfaces.rest;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import com.agentengine.tenancy.AccessControlService;
import com.agentengine.tenancy.RoleService;
import com.agentengine.tenancy.beans.Role;
import com.agentengine.util.context.ContextAware;
import com.agentengine.util.tenancy.SharingChange;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/v1/governance")
@Tag(name = "Governance", description = "Role and sharing management API")
@ContextAware
@RunOnVirtualThread
public class GovernanceRestAPI {

  private final AccessControlService accessControlService;
  private final RoleService roleService;

  @Inject
  public GovernanceRestAPI(
      final AccessControlService accessControlService, final RoleService roleService) {
    this.accessControlService = accessControlService;
    this.roleService = roleService;
  }

  @GET
  @Path("/role/{id}")
  @Produces(APPLICATION_JSON)
  @Operation(summary = "Get role by ID")
  public Role getRole(@PathParam("id") final String id) {
    return roleService.getRole(id);
  }

  @POST
  @Path("/role")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Operation(summary = "Create role")
  public Role createRole(final Role role) {
    return roleService.createRole(role);
  }

  @PUT
  @Path("/role/{id}")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Operation(summary = "Update role")
  public Role updateRole(@PathParam("id") final String id, final Role role) {
    return roleService.updateRole(id, role);
  }

  @DELETE
  @Path("/role/{id}")
  @Produces(APPLICATION_JSON)
  @Operation(summary = "Delete role")
  public void deleteRole(@PathParam("id") final String id) {
    roleService.deleteRole(id);
  }

  @POST
  @Path("/share")
  @Consumes(APPLICATION_JSON)
  @Produces(APPLICATION_JSON)
  @Operation(summary = "Update sharing")
  public void share(final List<SharingChange> changes) {
    accessControlService.updateSharing(changes);
  }
}
