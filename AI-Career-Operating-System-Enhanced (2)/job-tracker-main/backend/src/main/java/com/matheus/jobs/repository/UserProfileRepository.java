package com.matheus.jobs.repository;

import com.matheus.jobs.model.UserProfile;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class UserProfileRepository implements PanacheRepositoryBase<UserProfile, Integer> {

    public List<UserProfile> listProfiles() {
        return findAll(Sort.ascending("id")).list();
    }

    public Optional<UserProfile> getProfile(int id) {
        return findByIdOptional(id);
    }

    @Transactional
    public UserProfile saveProfile(UserProfile profile) {
        if (profile.id == null) {
            persist(profile);
            return profile;
        }
        UserProfile existing = findById(profile.id);
        if (existing == null) {
            persist(profile);
            return profile;
        }
        if (profile.name           != null) existing.name              = profile.name;
        if (profile.targetRoles    != null) existing.targetRoles       = profile.targetRoles;
        if (profile.skills         != null) existing.skills            = profile.skills;
        if (profile.experienceYears != null) existing.experienceYears  = profile.experienceYears;
        if (profile.workPreference != null) existing.workPreference    = profile.workPreference;
        if (profile.locationPreference != null) existing.locationPreference = profile.locationPreference;
        if (profile.highlights     != null) existing.highlights        = profile.highlights;
        if (profile.profileSummary != null) existing.profileSummary    = profile.profileSummary;
        existing.updatedAt = profile.updatedAt;
        return existing;
    }

    @Transactional
    public void deleteProfile(int id) {
        deleteById(id);
    }
}
