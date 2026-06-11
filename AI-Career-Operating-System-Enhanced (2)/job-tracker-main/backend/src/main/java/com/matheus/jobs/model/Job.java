package com.matheus.jobs.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.matheus.jobs.converter.JsonListConverter;
import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "jobs")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Job {

    @Id
    public String id;

    public Integer profileId;
    public String source;
    public String externalId;
    public String title;
    public String company;
    public String companyDomain;
    public String url;
    public String applyUrl;
    public String logoUrl;
    public String location;
    public boolean remote;

    @Column(columnDefinition = "TEXT")
    public String summary;

    public int matchScore;

    @Convert(converter = JsonListConverter.class)
    @Column(columnDefinition = "JSON")
    public List<String> keyPoints;

    @Convert(converter = JsonListConverter.class)
    @Column(columnDefinition = "JSON")
    public List<String> requirements;

    public String postedAt;
    public String firstSeenAt;
    public boolean applied;
    public boolean dismissed;

    public static String buildId(int profileId, String source, String externalId) {
        return profileId + "#" + source + "#" + externalId;
    }
}
