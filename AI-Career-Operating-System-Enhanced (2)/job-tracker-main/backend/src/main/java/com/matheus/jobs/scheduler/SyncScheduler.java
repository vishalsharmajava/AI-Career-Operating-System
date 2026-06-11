package com.matheus.jobs.scheduler;

import com.matheus.jobs.model.UserProfile;
import com.matheus.jobs.repository.UserProfileRepository;
import com.matheus.jobs.service.SyncService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class SyncScheduler {

    private static final Logger LOG = Logger.getLogger(SyncScheduler.class);

    @Inject SyncService syncService;
    @Inject UserProfileRepository profileRepository;

    @ConfigProperty(name = "jobs.sync.enabled", defaultValue = "true")
    boolean enabled;

    @Scheduled(every = "15m", delayed = "1m")
    void scheduledSync() {
        if (!enabled) return;
        List<UserProfile> profiles = profileRepository.listProfiles();
        if (profiles.isEmpty()) {
            LOG.debug("No profiles configured, skipping scheduled sync");
            return;
        }
        for (UserProfile profile : profiles) {
            LOG.infof("Scheduled sync for profile %d (%s)", profile.id, profile.name);
            syncService.sync(0, profile.id);
        }
    }
}
