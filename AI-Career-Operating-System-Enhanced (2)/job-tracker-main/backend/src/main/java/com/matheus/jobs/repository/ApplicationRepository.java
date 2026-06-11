package com.matheus.jobs.repository;

import com.matheus.jobs.model.Application;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class ApplicationRepository implements PanacheRepository<Application> {

    @Transactional
    public void saveApplication(Application app) {
        persist(app);
    }

    public List<Application> listApplications(int limit, int profileId) {
        return find("profileId = ?1", Sort.descending("appliedAt"), profileId)
                .page(0, limit).list();
    }

    @Transactional
    public void updateStatus(String jobId, String appliedAt, String status, int profileId) {
        update("status = ?1 WHERE jobId = ?2 AND appliedAt = ?3 AND profileId = ?4",
                status, jobId, appliedAt, profileId);
    }

    @Transactional
    public void deleteByProfileId(int profileId) {
        delete("profileId = ?1", profileId);
    }
}
