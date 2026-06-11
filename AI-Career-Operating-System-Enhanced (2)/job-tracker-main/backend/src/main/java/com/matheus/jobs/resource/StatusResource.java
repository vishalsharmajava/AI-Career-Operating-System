package com.matheus.jobs.resource;

import com.matheus.jobs.service.JobMatcherService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

@Path("/status")
@Produces(MediaType.APPLICATION_JSON)
public class StatusResource {

    @Inject JobMatcherService matcher;

    @GET
    public Map<String, Object> status() {
        return Map.of("aiEnabled", matcher.isAiEnabled());
    }
}
