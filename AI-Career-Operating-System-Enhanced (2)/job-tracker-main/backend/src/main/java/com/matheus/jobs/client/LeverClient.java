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
public class LeverClient {

    private static final Logger LOG = Logger.getLogger(LeverClient.class);
    private static final String BASE_URL = "https://api.lever.co/v0/postings/%s?mode=json&limit=100";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Inject ObjectMapper mapper;

    public List<Job> fetchJobs(String companySlug, String companyName, String domain, Instant sinceDate) {
        List<Job> jobs = new ArrayList<>();
        String url = BASE_URL.formatted(companySlug);
        if (sinceDate != null) {
            url += "&created_at_start=" + sinceDate.toEpochMilli();
        }

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                LOG.warnf("Lever %s returned %d", companySlug, resp.statusCode());
                return jobs;
            }

            JsonNode root = mapper.readTree(resp.body());
            // Lever returns either an array or { "data": [...] }
            JsonNode jobsArray = root.isArray() ? root : root.path("data");

            for (JsonNode node : jobsArray) {
                Job job = new Job();
                job.externalId  = node.path("id").asText();
                job.source      = "lever";
                // id assigned by SyncService
                job.title       = node.path("text").asText();
                job.company     = companyName;
                job.companyDomain = domain;
                job.url         = node.path("hostedUrl").asText();
                job.applyUrl    = node.path("applyUrl").asText();
                if (job.applyUrl == null || job.applyUrl.isBlank()) {
                    job.applyUrl = job.url;
                }
                job.logoUrl     = "https://logo.clearbit.com/" + domain;
                job.location    = extractLocation(node);
                job.remote      = isRemote(job.location);

                long createdAt = node.path("createdAt").asLong(0);
                if (createdAt > 0) {
                    job.postedAt = new java.util.Date(createdAt).toInstant().toString();
                }

                // Build raw description for LLM
                StringBuilder desc = new StringBuilder();
                JsonNode lists = node.path("lists");
                if (!lists.isMissingNode()) {
                    for (JsonNode list : lists) {
                        desc.append(list.path("text").asText()).append(": ");
                        desc.append(list.path("content").asText()).append("\n\n");
                    }
                }
                desc.append(node.path("description").asText(""));
                String content = desc.toString();
                job.summary = content.length() > 2000 ? content.substring(0, 2000) : content;

                jobs.add(job);
            }
        } catch (Exception e) {
            LOG.errorf("Error fetching Lever jobs for %s: %s", companySlug, e.getMessage());
        }
        return jobs;
    }

    private String extractLocation(JsonNode node) {
        JsonNode cats = node.path("categories");
        if (!cats.isMissingNode()) {
            return cats.path("location").asText("");
        }
        return node.path("workplaceType").asText("");
    }

    private boolean isRemote(String location) {
        if (location == null) return false;
        String l = location.toLowerCase();
        return l.contains("remote") || l.contains("anywhere");
    }
}
