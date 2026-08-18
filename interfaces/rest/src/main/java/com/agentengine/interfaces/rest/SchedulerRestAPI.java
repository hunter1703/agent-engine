package com.agentengine.interfaces.rest;

import com.agentengine.interfaces.rest.filter.ContextAware;
import com.agentengine.scheduler.api.models.JobDefinition;
import com.agentengine.scheduler.api.runner.SchedulerService;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/v1/scheduler")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ContextAware
public class SchedulerRestAPI {

  private final SchedulerService schedulerService;

  @Inject
  public SchedulerRestAPI(final SchedulerService schedulerService) {
    this.schedulerService = schedulerService;
  }

  @POST
  @Path("/jobs")
  public Response schedule(final JobDefinition jobDefinition) {
    schedulerService.schedule(jobDefinition);
    return Response.status(Response.Status.CREATED).entity(jobDefinition).build();
  }

  @PUT
  @Path("/jobs/{jobId}")
  public Response updateJob(
      @NotBlank @PathParam("jobId") final String jobId, final JobDefinition jobDefinition) {
    jobDefinition.setId(jobId);
    schedulerService.schedule(jobDefinition);
    return Response.ok(jobDefinition).build();
  }

  @GET
  @Path("/jobs/{jobId}")
  public Response getJob(@PathParam("jobId") final String jobId) {
    final JobDefinition jobDefinition = schedulerService.getJob(jobId);
    if (jobDefinition == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return Response.ok(jobDefinition).build();
  }

  @DELETE
  @Path("/jobs/{jobId}")
  public Response cancelJob(@PathParam("jobId") final String jobId) {
    schedulerService.cancelJob(jobId);
    return Response.noContent().build();
  }
}
