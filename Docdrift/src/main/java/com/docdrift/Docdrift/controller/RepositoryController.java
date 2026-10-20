package com.docdrift.Docdrift.controller;

import com.docdrift.Docdrift.dto.CreateRepositoryRequest;
import com.docdrift.Docdrift.dto.PageResponse;
import com.docdrift.Docdrift.model.RepositoryConfig;
import com.docdrift.Docdrift.model.RepositoryStatus;
import com.docdrift.Docdrift.repository.RepositoryConfigRepository;
import com.docdrift.Docdrift.service.DiffAnalyzerService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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

    // Only url and name are accepted from clients; id, status and timestamps are always set by the server
    @PostMapping
    public ResponseEntity<?> addRepository(@Valid @RequestBody CreateRepositoryRequest request) {
        String fullName = request.repositoryFromUrl();
        if (!fullName.equalsIgnoreCase(request.name())) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "name '" + request.name() + "' does not match the URL (expected " + fullName + ")."));
        }
        if (repository.existsByNameIgnoreCase(fullName)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", fullName + " is already tracked."));
        }

        RepositoryConfig config = new RepositoryConfig();
        config.setName(fullName);
        config.setUrl("https://github.com/" + fullName);
        config.setStatus(RepositoryStatus.HEALTHY);
        config.setCreatedAt(LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(config));
    }

    @GetMapping
    public PageResponse<RepositoryConfig> getRepositories(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("name"));
        return PageResponse.from(repository.findAll(pageRequest));
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
        if (config.getStatus() == RepositoryStatus.SCANNING) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "A scan is already running."));
        }
        // The scan runs in the background; the dashboard polls the repository status until it finishes
        diffAnalyzerService.scanLatestCommit(config);
        return ResponseEntity.accepted().body(repository.findById(id).orElse(config));
    }
}
