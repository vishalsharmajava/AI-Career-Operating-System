package com.matheus.jobs.resource;

import com.matheus.jobs.model.UserProfile;
import com.matheus.jobs.repository.ApplicationRepository;
import com.matheus.jobs.repository.JobRepository;
import com.matheus.jobs.repository.SyncRepository;
import com.matheus.jobs.repository.UserProfileRepository;
import com.matheus.jobs.service.ResumeService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Path("/profiles")
@Produces(MediaType.APPLICATION_JSON)
public class ProfileResource {

    @Inject UserProfileRepository profileRepository;
    @Inject JobRepository jobRepository;
    @Inject ApplicationRepository applicationRepository;
    @Inject SyncRepository syncRepository;
    @Inject ResumeService resumeService;

    @GET
    public List<UserProfile> list() {
        return profileRepository.listProfiles();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") int id) {
        return profileRepository.getProfile(id)
                .map(p -> Response.ok(p).build())
                .orElse(Response.status(404).build());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(Map<String, String> body) {
        String name = body != null ? body.get("name") : null;
        if (name == null || name.isBlank()) {
            return Response.status(400).entity(Map.of("error", "name is required")).build();
        }
        UserProfile profile = new UserProfile();
        profile.name      = name.strip();
        profile.updatedAt = Instant.now().toString();
        UserProfile saved = profileRepository.saveProfile(profile);
        return Response.ok(saved).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") int id) {
        if (profileRepository.getProfile(id).isEmpty()) {
            return Response.status(404).build();
        }
        jobRepository.deleteByProfileId(id);
        applicationRepository.deleteByProfileId(id);
        syncRepository.deleteByProfileId(id);
        profileRepository.deleteProfile(id);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/resume")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadResume(@PathParam("id") int id, @RestForm("file") FileUpload file) {
        UserProfile existing = profileRepository.getProfile(id)
                .orElse(null);
        if (existing == null) {
            return Response.status(404).entity(Map.of("error", "Profile not found")).build();
        }

        try {
            byte[] bytes   = Files.readAllBytes(file.uploadedFile());
            UserProfile ex = resumeService.extractProfile(bytes);
            ex.id          = id;
            ex.name        = existing.name;
            UserProfile saved = profileRepository.saveProfile(ex);
            return Response.ok(saved).build();
        } catch (Exception e) {
            return Response.status(500).entity(Map.of("error", e.getMessage())).build();
        }
    }
}
