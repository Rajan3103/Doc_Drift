package com.docdrift.Docdrift.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.BatchSize;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
public class DriftReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String commitSha;
    private String repositoryName;

    // Set when the report comes from a pull request check rather than a push to a watched branch
    private Integer pullRequestNumber;
    private String headBranch; // the PR's branch, only when it lives in the same repository (not a fork)
    
    private LocalDateTime analyzedAt = LocalDateTime.now();
    
    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL)
    @BatchSize(size = 50) // load suggestions for a whole page of reports in one query
    private List<DriftSuggestion> suggestions;
}
