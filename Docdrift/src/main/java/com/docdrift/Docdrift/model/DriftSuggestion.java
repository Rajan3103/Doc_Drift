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
    
    private String driftType; // API_SIGNATURE_MISMATCH, CONFIG_PROPERTY_CHANGED, CLI_USAGE_CHANGED, BEHAVIORAL_LOGIC_DRIFT, SAMPLE_CODE_BROKEN
    
    private String severity; // CRITICAL, HIGH, MEDIUM, LOW
    
    private Double confidenceScore; // 0.0 - 1.0
    
    private String impactedSymbol; // e.g. endpoint route, function name, config key
    
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
