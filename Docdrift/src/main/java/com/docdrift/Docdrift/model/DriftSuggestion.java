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
