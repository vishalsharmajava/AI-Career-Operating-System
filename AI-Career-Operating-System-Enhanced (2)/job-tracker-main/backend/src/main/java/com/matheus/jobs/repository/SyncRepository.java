package com.matheus.jobs.repository;

import com.matheus.jobs.model.SyncRecord;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class SyncRepository implements PanacheRepository<SyncRecord> {

    @Transactional
    public void saveSyncRecord(SyncRecord record) {
        persist(record);
    }

    public List<SyncRecord> listSyncRecords(int limit, int profileId) {
        return find("profileId = ?1", Sort.descending("syncedAt"), profileId)
                .page(0, limit).list();
    }

    public Optional<String> getLastSuccessfulSyncTime(int profileId) {
        return find("profileId = ?1 AND status = 'success' ORDER BY syncedAt DESC", profileId)
                .firstResultOptional()
                .map(r -> r.syncedAt);
    }

    @Transactional
    public void deleteByProfileId(int profileId) {
        delete("profileId = ?1", profileId);
    }
}
