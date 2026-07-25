package com.docdrift.Docdrift.controller;

import com.docdrift.Docdrift.dto.WebhookPayload;
import com.docdrift.Docdrift.model.RepositoryConfig;
import com.docdrift.Docdrift.repository.RepositoryConfigRepository;
import com.docdrift.Docdrift.service.DiffAnalyzerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/repos")
public class RepositoryController {

    private final RepositoryConfigRepository repository;
    private final DiffAnalyzerService diffAnalyzerService;

    public RepositoryController(RepositoryConfigRepository repository, DiffAnalyzerService diffAnalyzerService) {
        this.repository = repository;
        this.diffAnalyzerService = diffAnalyzerService;
    }

    @PostMapping
    public ResponseEntity<RepositoryConfig> addRepository(@RequestBody RepositoryConfig config) {
        if (config.getCreatedAt() == null) {
            config.setCreatedAt(LocalDateTime.now());
        }
        if (config.getStatus() == null) {
            config.setStatus("HEALTHY");
        }
        RepositoryConfig saved = repository.save(config);
        return ResponseEntity.ok(saved);
    }
    
    @GetMapping
    public ResponseEntity<List<RepositoryConfig>> getRepositories() {
        return ResponseEntity.ok(repository.findAll());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRepository(@PathVariable Long id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/scan")
    public ResponseEntity<RepositoryConfig> scanRepository(@PathVariable Long id) {
        return repository.findById(id).map(config -> {
            config.setStatus("SCANNING");
            config.setLastScannedAt(LocalDateTime.now());
            RepositoryConfig updated = repository.save(config);

            // Construct manual scan trigger payload
            WebhookPayload payload = new WebhookPayload();
            payload.setRef("refs/heads/main");
            
            WebhookPayload.RepositoryPayload repoPayload = new WebhookPayload.RepositoryPayload();
            repoPayload.setFull_name(config.getName());
            payload.setRepository(repoPayload);

            WebhookPayload.CommitPayload commit = new WebhookPayload.CommitPayload();
            commit.setId("HEAD");
            commit.setMessage("Manual Drift Scan Request");
            payload.setCommits(List.of(commit));

            diffAnalyzerService.processWebhookEvent(payload);

            config.setStatus("HEALTHY");
            return ResponseEntity.ok(repository.save(config));
        }).orElse(ResponseEntity.notFound().build());
    }
}
