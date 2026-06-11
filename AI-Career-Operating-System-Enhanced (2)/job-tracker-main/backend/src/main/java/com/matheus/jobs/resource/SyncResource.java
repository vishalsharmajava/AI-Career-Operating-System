package com.matheus.jobs.resource;

import com.matheus.jobs.model.SyncRecord;
import com.matheus.jobs.repository.SyncRepository;
import com.matheus.jobs.service.SyncService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/sync")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SyncResource {

    @Inject SyncService syncService;
    @Inject SyncRepository syncRepository;

    public record SyncRequest(int days) {}

    @POST
    public SyncRecord triggerSync(
            SyncRequest body,
            @QueryParam("profileId") @DefaultValue("1") int profileId) {
        int days = body != null ? body.days : 0;
        return syncService.sync(days, profileId);
    }

    @GET
    public List<SyncRecord> history(
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("profileId") @DefaultValue("1") int profileId) {
        return syncRepository.listSyncRecords(Math.min(limit, 100), profileId);
    }
}
