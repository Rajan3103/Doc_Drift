package com.docdrift.Docdrift.controller;

import com.docdrift.Docdrift.model.RepositoryConfig;
import com.docdrift.Docdrift.repository.RepositoryConfigRepository;
import com.docdrift.Docdrift.service.DiffAnalyzerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    public ResponseEntity<?> scanRepository(@PathVariable Long id) {
        RepositoryConfig config = repository.findById(id).orElse(null);
        if (config == null) {
            return ResponseEntity.notFound().build();
        }
        if ("SCANNING".equals(config.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "A scan is already running."));
        }
        // The scan runs in the background; the dashboard polls the repository status until it finishes
        diffAnalyzerService.scanLatestCommit(config);
        return ResponseEntity.accepted().body(repository.findById(id).orElse(config));
    }
}
