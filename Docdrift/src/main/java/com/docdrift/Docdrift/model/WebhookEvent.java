package com.docdrift.Docdrift.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class WebhookEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String eventType;
    private String commitSha;
    private String repositoryName;
    
    @Column(columnDefinition = "TEXT")
    private String payload;
    
    private LocalDateTime receivedAt = LocalDateTime.now();
    
    private String status = "PENDING"; // PENDING, PROCESSED, ERROR
}
