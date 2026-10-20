package com.docdrift.Docdrift.model;

import jakarta.persistence.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Data
public class DriftSuggestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String filePath;
    
    @Enumerated(EnumType.STRING)
    private DriftType driftType;
    
    @Enumerated(EnumType.STRING)
    private Severity severity;
    
    private Double confidenceScore; // 0.0 - 1.0
    
    private String impactedSymbol; // e.g. endpoint route, function name, config key
    
    private Boolean isBreakingChange = false; // true if breaking API change
    
    private String httpMethod; // GET, POST, PUT, DELETE, PATCH
    
    private String endpointPath; // e.g. /api/repos/{id}/scan
    
    private String schemaFormat; // OPENAPI_YAML, SWAGGER_JSON, MARKDOWN_TABLE, GENERAL_PROSE
    
    @Column(columnDefinition = "TEXT")
    private String oldText;
    
    @Column(columnDefinition = "TEXT")
    private String suggestedText;

    @Column(columnDefinition = "TEXT")
    private String originalSuggestedText; // the AI's text, kept when a reviewer edits suggestedText

    @Enumerated(EnumType.STRING)
    private SuggestionSource source = SuggestionSource.AI;
    
    @Column(columnDefinition = "TEXT")
    private String reason;
    
    @Enumerated(EnumType.STRING)
    private SuggestionStatus status = SuggestionStatus.PENDING;

    private String prUrl; // pull request opened when the suggestion was approved
    
    @ManyToOne
    @JoinColumn(name = "report_id")
    @JsonIgnore
    private DriftReport report;
}
