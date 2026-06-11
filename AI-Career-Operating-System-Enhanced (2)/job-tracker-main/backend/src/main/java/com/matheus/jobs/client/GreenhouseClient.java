package com.matheus.jobs.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matheus.jobs.model.Job;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class GreenhouseClient {

    private static final Logger LOG = Logger.getLogger(GreenhouseClient.class);
    private static final String BASE_URL = "https://boards-api.greenhouse.io/v1/boards/%s/jobs?content=true";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Inject ObjectMapper mapper;

    public List<Job> fetchJobs(String companySlug, String companyName, String domain, Instant sinceDate) {
        List<Job> jobs = new ArrayList<>();
        String url = BASE_URL.formatted(companySlug);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                LOG.warnf("Greenhouse %s returned %d", companySlug, resp.statusCode());
                return jobs;
            }

            JsonNode root = mapper.readTree(resp.body());
            JsonNode jobsArray = root.path("jobs");

            for (JsonNode node : jobsArray) {
                Job job = new Job();
                job.externalId  = node.path("id").asText();
                job.source      = "greenhouse";
                // id assigned by SyncService
                job.title       = node.path("title").asText();
                job.company     = companyName;
                job.companyDomain = domain;
                job.url         = node.path("absolute_url").asText();
                job.applyUrl    = job.url;
                job.logoUrl     = "https://logo.clearbit.com/" + domain;
                job.location    = extractLocation(node);
                job.remote      = isRemote(job.location, node);
                job.postedAt    = node.path("updated_at").asText();

                if (sinceDate != null && job.postedAt != null && !job.postedAt.isBlank()) {
                    try {
                        if (Instant.parse(job.postedAt).isBefore(sinceDate)) continue;
                    } catch (Exception ignored) {}
                }

                // Raw description for LLM processing
                String content = node.path("content").asText("");
                job.summary = content.length() > 2000 ? content.substring(0, 2000) : content;

                jobs.add(job);
            }
        } catch (Exception e) {
            LOG.errorf("Error fetching Greenhouse jobs for %s: %s", companySlug, e.getMessage());
        }
        return jobs;
    }

    private String extractLocation(JsonNode node) {
        JsonNode loc = node.path("location");
        if (!loc.isMissingNode()) return loc.path("name").asText("");
        return "";
    }

    private boolean isRemote(String location, JsonNode node) {
        if (location == null) return false;
        String l = location.toLowerCase();
        return l.contains("remote") || l.contains("anywhere");
    }
}
