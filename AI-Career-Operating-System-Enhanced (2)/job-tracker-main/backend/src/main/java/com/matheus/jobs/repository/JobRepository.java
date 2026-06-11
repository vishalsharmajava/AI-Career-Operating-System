package com.matheus.jobs.repository;

import com.matheus.jobs.model.Job;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class JobRepository implements PanacheRepositoryBase<Job, String> {

    public boolean jobExists(String id) {
        return findByIdOptional(id).isPresent();
    }

    @Transactional
    public void saveJob(Job job) {
        Job existing = findById(job.id);
        if (existing == null) {
            persist(job);
        } else {
            existing.title        = job.title;
            existing.summary      = job.summary;
            existing.matchScore   = job.matchScore;
            existing.keyPoints    = job.keyPoints;
            existing.requirements = job.requirements;
            existing.postedAt     = job.postedAt;
        }
    }

    public List<Job> listJobs(int limit, int profileId) {
        return find("profileId = ?1", Sort.descending("firstSeenAt"), profileId)
                .page(0, limit).list();
    }

    public Optional<Job> findJob(String id) {
        return findByIdOptional(id);
    }

    @Transactional
    public void markApplied(String id, boolean applied) {
        update("applied = ?1 WHERE id = ?2", applied, id);
    }

    @Transactional
    public void markDismissed(String id, boolean dismissed) {
        update("dismissed = ?1 WHERE id = ?2", dismissed, id);
    }

    @Transactional
    public void deleteByProfileId(int profileId) {
        delete("profileId = ?1", profileId);
    }
}
