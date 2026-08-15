package com.agentengine.interfaces.rest;

import com.agentengine.scheduler.api.models.Job;
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
public class SchedulerRestAPI {

    private final SchedulerService schedulerService;

    @Inject
    public SchedulerRestAPI(final SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    @POST
    @Path("/jobs")
    public Response schedule(final Job job) {
        if (schedulerService.schedule(job)) {
            return Response.status(Response.Status.CREATED).entity(job).build();
        }
        return Response.status(Response.Status.BAD_REQUEST).build();
    }

    @PUT
    @Path("/jobs/{jobId}")
    public Response updateJob(@NotBlank @PathParam("jobId") final String jobId, final Job job) {
        job.setId(jobId);
        if (schedulerService.schedule(job)) {
            return Response.ok(job).build();
        }
        return Response.status(Response.Status.BAD_REQUEST).build();
    }

    @GET
    @Path("/jobs/{jobId}")
    public Response getJob(@PathParam("jobId") final String jobId) {
        final Job job = schedulerService.getJob(jobId);
        if (job == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(job).build();
    }

    @DELETE
    @Path("/jobs/{jobId}")
    public Response cancelJob(@PathParam("jobId") final String jobId) {
        schedulerService.cancelJob(jobId);
        return Response.noContent().build();
    }
}
