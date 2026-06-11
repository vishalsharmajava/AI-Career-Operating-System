package com.matheus.jobs.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

@Entity
@Table(name = "applications")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @JsonIgnore
    public Long dbId;

    public Integer profileId;
    public String jobId;
    public String jobTitle;
    public String company;
    public String applyUrl;
    public String appliedAt;

    @Column(columnDefinition = "TEXT")
    public String notes;

    public String status;
}
