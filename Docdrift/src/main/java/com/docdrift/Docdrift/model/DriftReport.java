package com.docdrift.Docdrift.model;

import jakarta.persistence.*;
import lombok.Data;
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
    
    private LocalDateTime analyzedAt = LocalDateTime.now();
    
    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL)
    private List<DriftSuggestion> suggestions;
}
