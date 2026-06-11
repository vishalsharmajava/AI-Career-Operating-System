package com.matheus.jobs.resource;

import com.matheus.jobs.model.Application;
import com.matheus.jobs.repository.ApplicationRepository;
import com.matheus.jobs.repository.JobRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Path("/applications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ApplicationResource {

    @Inject JobRepository jobRepository;
    @Inject ApplicationRepository applicationRepository;

    @GET
    public List<Application> list(
            @QueryParam("limit") @DefaultValue("50") int limit,
            @QueryParam("profileId") @DefaultValue("1") int profileId) {
        return applicationRepository.listApplications(Math.min(limit, 200), profileId);
    }

    @POST
    @Path("/{jobId}/apply")
    public Response apply(
            @PathParam("jobId") String jobId,
            @QueryParam("profileId") @DefaultValue("1") int profileId,
            Map<String, String> body) {

        String decodedId = jobId.replace("%23", "#");

        return jobRepository.findJob(decodedId).map(job -> {
            Application app = new Application();
            app.profileId = profileId;
            app.jobId     = decodedId;
            app.jobTitle  = job.title;
            app.company   = job.company;
            app.applyUrl  = job.applyUrl;
            app.appliedAt = Instant.now().toString();
            app.notes     = body != null ? body.getOrDefault("notes", "") : "";
            app.status    = "applied";

            applicationRepository.saveApplication(app);
            jobRepository.markApplied(decodedId, true);

            return Response.ok(app).build();
        }).orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Job not found")).build());
    }

    @PATCH
    @Path("/{jobId}/{appliedAt}/status")
    public Response updateStatus(
            @PathParam("jobId") String jobId,
            @PathParam("appliedAt") String appliedAt,
            @QueryParam("profileId") @DefaultValue("1") int profileId,
            Map<String, String> body) {

        String decodedId = jobId.replace("%23", "#");
        String newStatus = body != null ? body.get("status") : null;
        if (newStatus == null || newStatus.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "status is required")).build();
        }

        applicationRepository.updateStatus(decodedId, appliedAt, newStatus, profileId);
        return Response.ok(Map.of("status", newStatus)).build();
    }
}
