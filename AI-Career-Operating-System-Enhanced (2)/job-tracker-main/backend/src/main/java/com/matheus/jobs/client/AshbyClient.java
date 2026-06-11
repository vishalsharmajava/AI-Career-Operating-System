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
public class AshbyClient {

    private static final Logger LOG = Logger.getLogger(AshbyClient.class);
    private static final String BASE_URL = "https://api.ashbyhq.com/posting-api/job-board/%s";

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
                LOG.warnf("Ashby %s returned %d", companySlug, resp.statusCode());
                return jobs;
            }

            JsonNode root = mapper.readTree(resp.body());
            JsonNode jobsArray = root.path("jobs");

            for (JsonNode node : jobsArray) {
                if (!node.path("isListed").asBoolean(true)) continue;

                Job job = new Job();
                job.externalId    = node.path("id").asText();
                job.source        = "ashby";
                // id assigned by SyncService
                job.title         = node.path("title").asText();
                job.company       = companyName;
                job.companyDomain = domain;
                job.url           = node.path("jobUrl").asText();
                job.applyUrl      = node.path("applyUrl").asText();
                if (job.applyUrl == null || job.applyUrl.isBlank()) job.applyUrl = job.url;
                job.logoUrl       = "https://logo.clearbit.com/" + domain;
                job.location      = node.path("location").asText("");
                job.remote        = node.path("isRemote").asBoolean(false)
                        || "Remote".equalsIgnoreCase(node.path("workplaceType").asText());
                job.postedAt      = node.path("publishedAt").asText("");

                if (sinceDate != null && !job.postedAt.isBlank()) {
                    try {
                        if (Instant.parse(job.postedAt).isBefore(sinceDate)) continue;
                    } catch (Exception ignored) {}
                }

                String desc = node.path("descriptionHtml").asText("");
                job.summary = desc.length() > 2000 ? desc.substring(0, 2000) : desc;

                jobs.add(job);
            }
        } catch (Exception e) {
            LOG.errorf("Error fetching Ashby jobs for %s: %s", companySlug, e.getMessage());
        }
        return jobs;
    }
}
