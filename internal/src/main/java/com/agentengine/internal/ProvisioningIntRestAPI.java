package com.agentengine.internal;

import com.agentengine.tenancy.ProvisioningRequest;
import com.agentengine.util.context.ContextAware;
import com.agentengine.tenancy.ProvisioningResult;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@ContextAware
@Path("/provision")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProvisioningIntRestAPI {

  private static final int MULTI_STATUS = 207;

  private final EnvironmentProvisioningService environmentProvisioningService;
  private final CustomerProvisioningService customerProvisioningService;

  @Inject
  public ProvisioningIntRestAPI(
      final EnvironmentProvisioningService environmentProvisioningService,
      final CustomerProvisioningService customerProvisioningService) {
    this.environmentProvisioningService = environmentProvisioningService;
    this.customerProvisioningService = customerProvisioningService;
  }

  /** Sets up what all customers share, and the system customer. */
  @POST
  @Path("/environment")
  public Response provisionEnvironment(final ProvisioningRequest provisioningRequest) {
    return response(environmentProvisioningService.provisionEnvironment(provisioningRequest));
  }

  @POST
  @Path("/customer")
  public Response provisionCustomer(final CustomerProvisioningRequest request) {
    return response(customerProvisioningService.provisionCustomer(request));
  }

  private static Response response(final ProvisioningResult result) {
    return Response.status(result.succeeded() ? Response.Status.OK.getStatusCode() : MULTI_STATUS)
        .entity(result)
        .build();
  }
}
