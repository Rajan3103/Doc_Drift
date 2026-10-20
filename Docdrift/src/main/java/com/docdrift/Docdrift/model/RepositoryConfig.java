package com.docdrift.Docdrift.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

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
}
