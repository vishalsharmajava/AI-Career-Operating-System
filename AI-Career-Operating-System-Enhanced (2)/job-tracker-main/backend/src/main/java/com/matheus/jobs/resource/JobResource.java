package com.matheus.jobs.resource;

import com.matheus.jobs.model.Job;
import com.matheus.jobs.repository.JobRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/jobs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class JobResource {

    @Inject JobRepository repository;

    @GET
    public List<Job> list(
            @QueryParam("limit") @DefaultValue("50") int limit,
            @QueryParam("profileId") @DefaultValue("1") int profileId) {
        return repository.listJobs(Math.min(limit, 200), profileId);
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") String id) {
        String decodedId = id.replace("%23", "#");
        return repository.findJob(decodedId)
                .map(job -> Response.ok(job).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Path("/{id}/dismiss")
    public Response dismiss(@PathParam("id") String id) {
        String decodedId = id.replace("%23", "#");
        repository.markDismissed(decodedId, true);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/undismiss")
    public Response undismiss(@PathParam("id") String id) {
        String decodedId = id.replace("%23", "#");
        repository.markDismissed(decodedId, false);
        return Response.noContent().build();
    }
}
