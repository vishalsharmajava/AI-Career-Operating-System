package com.matheus.jobs.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matheus.jobs.model.Job;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class RemotiveClient {

    private static final Logger LOG = Logger.getLogger(RemotiveClient.class);
    private static final String BASE_URL =
            "https://remotive.com/api/remote-jobs?category=software-dev&search=%s&limit=50";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Inject ObjectMapper mapper;

    public List<Job> fetchJobs(String searchQuery, Instant sinceDate) {
        List<Job> jobs = new ArrayList<>();
        String encoded = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8);
        String url = BASE_URL.formatted(encoded);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                LOG.warnf("Remotive returned %d for query '%s'", resp.statusCode(), searchQuery);
                return jobs;
            }

            JsonNode root = mapper.readTree(resp.body());
            JsonNode jobsArray = root.path("jobs");

            for (JsonNode node : jobsArray) {
                Job job = new Job();
                job.externalId  = String.valueOf(node.path("id").asLong());
                job.source      = "remotive";
                // id assigned by SyncService
                job.title       = node.path("title").asText();
                job.company     = node.path("company_name").asText();
                job.companyDomain = extractDomain(node.path("company_logo").asText(""));
                job.url         = node.path("url").asText();
                job.applyUrl    = job.url;
                job.logoUrl     = node.path("company_logo").asText("");
                job.location    = "Remote";
                job.remote      = true;
                job.postedAt    = node.path("publication_date").asText();

                if (sinceDate != null && job.postedAt != null && !job.postedAt.isBlank()) {
                    try {
                        if (Instant.parse(job.postedAt + "Z").isBefore(sinceDate)) continue;
                    } catch (Exception ignored) {}
                }

                String desc = node.path("description").asText("");
                job.summary = desc.length() > 2000 ? desc.substring(0, 2000) : desc;

                jobs.add(job);
            }
        } catch (Exception e) {
            LOG.errorf("Error fetching Remotive jobs: %s", e.getMessage());
        }
        return jobs;
    }

    private String extractDomain(String logoUrl) {
        if (logoUrl == null || logoUrl.isBlank()) return "";
        try {
            URI uri = URI.create(logoUrl);
            return uri.getHost();
        } catch (Exception e) {
            return "";
        }
    }
}
