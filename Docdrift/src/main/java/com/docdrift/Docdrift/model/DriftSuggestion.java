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
    
    private String driftType; // API_SIGNATURE_MISMATCH, CONFIG_PROPERTY_CHANGED, CLI_USAGE_CHANGED, BEHAVIORAL_LOGIC_DRIFT, SAMPLE_CODE_BROKEN, API_CONTRACT_BREAKING_CHANGE, SCHEMA_DRIFT
    
    private String severity; // CRITICAL, HIGH, MEDIUM, LOW
    
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
    private String reason;
    
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED
    
    @ManyToOne
    @JoinColumn(name = "report_id")
    @JsonIgnore
    private DriftReport report;
}
