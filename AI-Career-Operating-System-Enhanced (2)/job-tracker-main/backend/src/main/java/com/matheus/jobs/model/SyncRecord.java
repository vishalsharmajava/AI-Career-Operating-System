package com.matheus.jobs.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

@Entity
@Table(name = "sync_records")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SyncRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @JsonIgnore
    public Long dbId;

    public Integer profileId;
    public String syncedAt;
    public String sinceDate;
    public int newJobsCount;
    public int totalChecked;
    public int matchedCount;
    public String sources;
    public String status;
    public String errorMessage;
}
