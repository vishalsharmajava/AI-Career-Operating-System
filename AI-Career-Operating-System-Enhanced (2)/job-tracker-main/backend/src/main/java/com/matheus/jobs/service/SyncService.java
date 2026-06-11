package com.matheus.jobs.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matheus.jobs.client.AshbyClient;
import com.matheus.jobs.client.GreenhouseClient;
import com.matheus.jobs.client.LeverClient;
import com.matheus.jobs.client.RemotiveClient;
import com.matheus.jobs.model.Job;
import com.matheus.jobs.model.SyncRecord;
import com.matheus.jobs.model.UserProfile;
import com.matheus.jobs.repository.JobRepository;
import com.matheus.jobs.repository.SyncRepository;
import com.matheus.jobs.repository.UserProfileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class SyncService {

    private static final Logger LOG = Logger.getLogger(SyncService.class);

    @Inject GreenhouseClient greenhouse;
    @Inject LeverClient lever;
    @Inject AshbyClient ashby;
    @Inject RemotiveClient remotive;
    @Inject JobMatcherService matcher;
    @Inject JobRepository jobRepository;
    @Inject SyncRepository syncRepository;
    @Inject UserProfileRepository profileRepository;
    @Inject ObjectMapper objectMapper;

    public SyncRecord sync(int days, int profileId) {
        String syncedAt = Instant.now().toString();
        AtomicInteger totalChecked = new AtomicInteger(0);
        AtomicInteger newJobs      = new AtomicInteger(0);

        Instant sinceDate;
        if (days > 0) {
            sinceDate = Instant.now().minus(days, ChronoUnit.DAYS);
        } else {
            Instant fifteenDaysAgo = Instant.now().minus(15, ChronoUnit.DAYS);
            sinceDate = syncRepository.getLastSuccessfulSyncTime(profileId)
                    .map(Instant::parse)
                    .filter(t -> t.isAfter(fifteenDaysAgo))
                    .orElse(fifteenDaysAgo);
        }
        LOG.infof("Syncing profile %d since %s (days=%d)", profileId, sinceDate, days);

        UserProfile profile = profileRepository.getProfile(profileId).orElse(null);

        SyncRecord record = new SyncRecord();
        record.profileId = profileId;
        record.syncedAt  = syncedAt;
        record.status    = "success";
        record.sinceDate = sinceDate.toString();

        try {
            List<Map<String, String>> companies = loadCompanies();
            List<Job> rawJobs = new ArrayList<>();

            for (Map<String, String> company : companies) {
                String source = company.get("source");
                String slug   = company.get("slug");
                String name   = company.get("name");
                String domain = company.get("domain");

                List<Job> fetched = switch (source) {
                    case "greenhouse" -> greenhouse.fetchJobs(slug, name, domain, sinceDate);
                    case "lever"      -> lever.fetchJobs(slug, name, domain, sinceDate);
                    case "ashby"      -> ashby.fetchJobs(slug, name, domain, sinceDate);
                    default           -> List.of();
                };
                rawJobs.addAll(fetched);
                LOG.debugf("Fetched %d jobs from %s/%s", fetched.size(), source, slug);
            }

            rawJobs.addAll(remotive.fetchJobs("java tech lead", sinceDate));
            rawJobs.addAll(remotive.fetchJobs("java staff engineer", sinceDate));

            totalChecked.set(rawJobs.size());
            LOG.infof("Total raw jobs fetched: %d (profile %d)", totalChecked.get(), profileId);

            // Assign IDs and filter out already-known and pre-filter rejects before calling Claude
            List<Job> candidates = new ArrayList<>();
            for (Job job : rawJobs) {
                job.id        = Job.buildId(profileId, job.source, job.externalId);
                job.profileId = profileId;
                job.firstSeenAt = syncedAt;

                if (jobRepository.jobExists(job.id))  continue;
                if (!matcher.passesPreFilter(job))     continue;
                candidates.add(job);
            }
            LOG.infof("Candidates after pre-filter: %d / %d (profile %d)",
                    candidates.size(), rawJobs.size(), profileId);

            // Batch match against profile
            List<Job> enriched = matcher.enrichAndFilterBatch(candidates, profile);
            for (Job job : enriched) {
                jobRepository.saveJob(job);
                newJobs.incrementAndGet();
            }

            record.newJobsCount = newJobs.get();
            record.totalChecked = totalChecked.get();
            record.matchedCount = enriched.size();
            record.sources      = "greenhouse,lever,ashby,remotive";

        } catch (Exception e) {
            LOG.errorf("Sync failed for profile %d: %s", profileId, e.getMessage(), e);
            record.status       = "error";
            record.errorMessage = e.getMessage();
        }

        syncRepository.saveSyncRecord(record);
        LOG.infof("Sync complete (profile %d): %d new jobs out of %d checked (matched: %d)",
                profileId, newJobs.get(), totalChecked.get(), record.matchedCount);
        return record;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> loadCompanies() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/companies.json")) {
            if (is == null) throw new IllegalStateException("companies.json not found");
            return objectMapper.readValue(is, new TypeReference<>() {});
        }
    }
}
