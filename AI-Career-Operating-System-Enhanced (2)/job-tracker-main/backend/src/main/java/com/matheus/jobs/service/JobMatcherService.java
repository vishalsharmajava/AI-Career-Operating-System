package com.matheus.jobs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matheus.jobs.model.Job;
import com.matheus.jobs.model.UserProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class JobMatcherService {

    private static final Logger LOG = Logger.getLogger(JobMatcherService.class);
    private static final String ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";

    private static final int MIN_MATCH_SCORE      = 40;
    private static final int BATCH_SIZE           = 8;
    private static final int MAX_DESCRIPTION_CHARS = 1_500;

    // Pre-filter #1: title must contain at least one of these
    private static final List<String> TITLE_KEYWORDS = List.of(
            "engineer", "developer", "desenvolvedor", "engenheiro",
            "tech lead", "techlead", "staff", "principal", "architect",
            "engineering manager", "cto", "vp of engineering"
    );

    // Pre-filter #2: description must mention Java/JVM technology
    private static final List<String> TECH_KEYWORDS = List.of(
            "java", "jvm", "spring", "quarkus", "micronaut",
            "kotlin", "scala", "groovy", "maven", "gradle", "hibernate"
    );

    @ConfigProperty(name = "jobs.anthropic.api-key")
    String apiKey;

    @ConfigProperty(name = "jobs.anthropic.model")
    String model;

    @Inject ObjectMapper mapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Returns true if the job passes both title and description pre-filters (no API call). */
    public boolean passesPreFilter(Job job) {
        String title = job.title != null ? job.title.toLowerCase() : "";
        if (TITLE_KEYWORDS.stream().noneMatch(title::contains)) {
            LOG.debugf("Skipped by title: %s — %s", job.company, job.title);
            return false;
        }
        String desc = (job.summary != null ? job.summary : "").toLowerCase();
        if (TECH_KEYWORDS.stream().noneMatch(desc::contains)) {
            LOG.debugf("Skipped by tech keywords: %s — %s", job.company, job.title);
            return false;
        }
        return true;
    }

    public boolean isAiEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Scores a list of jobs against a profile in batches of {@value BATCH_SIZE}.
     * Each description is truncated to {@value MAX_DESCRIPTION_CHARS} chars before
     * being sent. Returns only jobs that matched (score >= 40), already enriched
     * with summary, keyPoints and requirements.
     *
     * When no API key is configured, all pre-filtered jobs are saved with matchScore=0
     * (no AI scoring).
     */
    public List<Job> enrichAndFilterBatch(List<Job> jobs, UserProfile profile) {
        if (jobs.isEmpty()) return List.of();

        if (!isAiEnabled()) {
            LOG.info("AI matching disabled (no ANTHROPIC_API_KEY) — saving all pre-filtered jobs");
            return jobs;
        }

        List<Job> matched  = new ArrayList<>();
        String systemPrompt = buildSystemPrompt(profile);

        for (int offset = 0; offset < jobs.size(); offset += BATCH_SIZE) {
            List<Job> batch = jobs.subList(offset, Math.min(offset + BATCH_SIZE, jobs.size()));
            LOG.infof("Calling Claude for batch of %d jobs (offset %d)", batch.size(), offset);
            matched.addAll(processBatch(batch, systemPrompt));
        }

        return matched;
    }

    // ── Private helpers ──────────────────────────────────────────────────────────

    private List<Job> processBatch(List<Job> batch, String systemPrompt) {
        String userMessage = buildBatchMessage(batch);

        try {
            String requestBody = mapper.writeValueAsString(Map.of(
                    "model", model,
                    "max_tokens", 2048,
                    "system", systemPrompt,
                    "messages", List.of(Map.of("role", "user", "content", userMessage))
            ));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ANTHROPIC_URL))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                LOG.warnf("Anthropic API returned %d for batch: %s", resp.statusCode(), resp.body());
                return List.of();
            }

            String content = mapper.readTree(resp.body())
                    .path("content").get(0).path("text").asText();

            return parseBatchResponse(content, batch);

        } catch (Exception e) {
            LOG.errorf("Batch call failed: %s", e.getMessage(), e);
            return List.of();
        }
    }

    private String buildBatchMessage(List<Job> batch) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < batch.size(); i++) {
            Job job  = batch.get(i);
            String desc = sanitize(job.summary);
            if (desc.length() > MAX_DESCRIPTION_CHARS) desc = desc.substring(0, MAX_DESCRIPTION_CHARS);

            sb.append("[JOB ").append(i).append("]\n")
              .append("Title: ").append(job.title).append("\n")
              .append("Company: ").append(job.company).append("\n")
              .append("Location: ").append(job.location).append("\n")
              .append("Description:\n").append(desc).append("\n\n");
        }
        return sb.toString();
    }

    private List<Job> parseBatchResponse(String content, List<Job> batch) {
        int start = content.indexOf('[');
        int end   = content.lastIndexOf(']');
        if (start < 0 || end <= start) {
            LOG.warnf("Batch response has no JSON array: %s", content);
            return List.of();
        }

        List<Job> matched = new ArrayList<>();
        try {
            JsonNode array = mapper.readTree(content.substring(start, end + 1));
            for (JsonNode node : array) {
                int index      = node.path("index").asInt(-1);
                boolean isMatch = node.path("isMatch").asBoolean(false);
                int score       = node.path("matchScore").asInt(0);

                if (index < 0 || index >= batch.size()) continue;
                if (!isMatch || score < MIN_MATCH_SCORE)  continue;

                Job job          = batch.get(index);
                job.matchScore   = score;
                job.summary      = node.path("summary").asText(job.summary);
                job.keyPoints    = toList(node.path("keyPoints"));
                job.requirements = toList(node.path("requirements"));

                LOG.infof("Job matched: %s — %s (score=%d)", job.company, job.title, score);
                matched.add(job);
            }
        } catch (Exception e) {
            LOG.errorf("Failed to parse batch response: %s", e.getMessage());
        }
        return matched;
    }

    private String buildSystemPrompt(UserProfile profile) {
        String base;
        if (profile == null || profile.profileSummary == null || profile.profileSummary.isBlank()) {
            base = """
                    You are a job matching assistant for a Senior Java engineer with 10+ years of experience.
                    Target roles: Tech Lead, Staff Engineer, Principal Engineer, Engineering Manager.
                    Java is required as the primary technology. Remote-first preferred.""";
        } else {
            String roles  = profile.targetRoles    != null ? String.join(", ", profile.targetRoles) : "Tech Lead, Staff Engineer";
            String skills = profile.skills         != null ? String.join(", ", profile.skills)       : "Java";
            String exp    = profile.experienceYears != null ? profile.experienceYears + " years"     : "10+ years";
            String work   = profile.workPreference != null ? profile.workPreference                  : "remote";
            base = """
                    You are a job matching assistant for this candidate:
                    %s
                    Target roles: %s | Skills: %s | Experience: %s | Work: %s"""
                    .formatted(profile.profileSummary, roles, skills, exp, work);
        }

        return base + """


                You will receive multiple job postings labeled [JOB 0], [JOB 1], etc.
                Respond ONLY with a valid JSON array, one object per job, in index order:
                [
                  {"index": 0, "isMatch": true, "matchScore": 75, "summary": "2-3 sentences", "keyPoints": ["..."], "requirements": ["..."]},
                  {"index": 1, "isMatch": false, "matchScore": 20}
                ]

                Rules:
                - isMatch = true only when the role and required skills align with the candidate profile
                - matchScore = 0-100
                - Include summary/keyPoints/requirements ONLY for isMatch=true entries (3-5 items, under 100 chars each)
                - No markdown, no explanation — only the JSON array""";
    }

    private String sanitize(String text) {
        if (text == null) return "";
        return text
                .replaceAll("<[^>]*>", " ")
                .replaceAll("&[a-zA-Z]+;", " ")
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "")
                .replaceAll("\\s{3,}", "\n\n")
                .strip();
    }

    private List<String> toList(JsonNode array) {
        if (array == null || array.isMissingNode() || !array.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonNode node : array) {
            String text = node.asText();
            if (!text.isBlank()) result.add(text);
        }
        return result;
    }
}
