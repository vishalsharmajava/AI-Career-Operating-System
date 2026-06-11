package com.matheus.jobs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matheus.jobs.model.UserProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ResumeService {

    private static final Logger LOG = Logger.getLogger(ResumeService.class);
    private static final String ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";
    private static final int MAX_RESUME_CHARS = 12_000;

    @Inject ObjectMapper mapper;

    @ConfigProperty(name = "jobs.anthropic.api-key")
    String apiKey;

    @ConfigProperty(name = "jobs.anthropic.model")
    String model;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public boolean isAiEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    public UserProfile extractProfile(byte[] pdfBytes) throws Exception {
        if (!isAiEnabled()) {
            throw new IllegalStateException(
                "ANTHROPIC_API_KEY is not configured. Add it to backend/.env to enable resume analysis.");
        }
        String text = extractText(pdfBytes);
        LOG.infof("Extracted %d characters from resume PDF", text.length());
        return callClaude(text);
    }

    private String extractText(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private UserProfile callClaude(String resumeText) throws Exception {
        String truncated = resumeText.length() > MAX_RESUME_CHARS
                ? resumeText.substring(0, MAX_RESUME_CHARS)
                : resumeText;

        String prompt = """
                Analyze this resume and extract structured profile information for job matching.
                Return ONLY valid JSON — no markdown, no explanation:
                {
                  "targetRoles": ["Tech Lead", "Staff Engineer"],
                  "skills": ["Java", "Spring Boot", "Kafka", "AWS"],
                  "experienceYears": 10,
                  "workPreference": "remote",
                  "locationPreference": "Americas (remote)",
                  "highlights": ["Led migration from monolith to microservices", "Managed team of 8"],
                  "profileSummary": "2-3 sentence professional summary focused on seniority, stack, and leadership"
                }

                Resume:
                %s
                """.formatted(truncated);

        String requestBody = mapper.writeValueAsString(Map.of(
                "model", model,
                "max_tokens", 1024,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        ));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ANTHROPIC_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Claude API error " + resp.statusCode() + ": " + resp.body());
        }

        JsonNode response = mapper.readTree(resp.body());
        String content   = response.path("content").get(0).path("text").asText();

        int start = content.indexOf('{');
        int end   = content.lastIndexOf('}');
        JsonNode result = mapper.readTree(start >= 0 && end > start
                ? content.substring(start, end + 1) : content);

        UserProfile profile = new UserProfile();
        profile.targetRoles        = toList(result.path("targetRoles"));
        profile.skills             = toList(result.path("skills"));
        profile.experienceYears    = result.path("experienceYears").asInt(0);
        profile.workPreference     = result.path("workPreference").asText("remote");
        profile.locationPreference = result.path("locationPreference").asText("anywhere");
        profile.highlights         = toList(result.path("highlights"));
        profile.profileSummary     = result.path("profileSummary").asText();
        profile.updatedAt          = Instant.now().toString();

        LOG.infof("Profile extracted: %d skills, %d years exp, roles: %s",
                profile.skills != null ? profile.skills.size() : 0,
                profile.experienceYears,
                profile.targetRoles);

        return profile;
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
