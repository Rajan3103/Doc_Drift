package com.docdrift.Docdrift.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String url;
    
    @Column(nullable = false)
    private String name; // e.g., owner/repo

    @Enumerated(EnumType.STRING)
    private RepositoryStatus status = RepositoryStatus.HEALTHY;

    @Column(columnDefinition = "TEXT")
    private String lastError; // why the most recent scan failed, if it did

    private LocalDateTime lastScannedAt;

    private LocalDateTime createdAt = LocalDateTime.now();

    // ---- Per-repository settings ----

    /** Doc files/folders/globs to check; empty means discover docs automatically. */
    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> docPaths = new ArrayList<>();

    /** Branch names or globs whose pushes are analysed; empty means the default branch only. */
    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> watchedBranches = new ArrayList<>();

    /** Suggestions below this severity are discarded. */
    @Enumerated(EnumType.STRING)
    private Severity minSeverity = Severity.LOW;

    /** Suggestions below this confidence (0.0 - 1.0) are discarded. */
    private Double minConfidence = 0.0;

    /** Analyse pull requests into watched branches and comment on them. */
    private Boolean analyzePullRequests = true;

    /** Slack incoming webhook for critical / breaking drift alerts. Secret, so never sent to the dashboard. */
    @JsonIgnore
    @Column(columnDefinition = "TEXT")
    private String slackWebhookUrl;

    /** Email address for critical / breaking drift alerts. */
    private String alertEmail;

    public boolean isSlackAlertsConfigured() {
        return slackWebhookUrl != null && !slackWebhookUrl.isBlank();
    }
}
