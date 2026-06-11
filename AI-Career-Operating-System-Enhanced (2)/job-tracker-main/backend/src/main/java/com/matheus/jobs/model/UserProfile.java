package com.matheus.jobs.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.matheus.jobs.converter.JsonListConverter;
import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "user_profile")
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    public String name;

    @Convert(converter = JsonListConverter.class)
    @Column(columnDefinition = "JSON")
    public List<String> targetRoles;

    @Convert(converter = JsonListConverter.class)
    @Column(columnDefinition = "JSON")
    public List<String> skills;

    public Integer experienceYears;
    public String workPreference;
    public String locationPreference;

    @Convert(converter = JsonListConverter.class)
    @Column(columnDefinition = "JSON")
    public List<String> highlights;

    @Column(columnDefinition = "TEXT")
    public String profileSummary;

    public String updatedAt;
}
