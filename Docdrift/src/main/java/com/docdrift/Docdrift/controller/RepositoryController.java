package com.docdrift.Docdrift.controller;

import com.docdrift.Docdrift.model.RepositoryConfig;
import com.docdrift.Docdrift.repository.RepositoryConfigRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/repos")
public class RepositoryController {

    private final RepositoryConfigRepository repository;

    public RepositoryController(RepositoryConfigRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<RepositoryConfig> addRepository(@RequestBody RepositoryConfig config) {
        RepositoryConfig saved = repository.save(config);
        return ResponseEntity.ok(saved);
    }
    
    @GetMapping
    public ResponseEntity<List<RepositoryConfig>> getRepositories() {
        return ResponseEntity.ok(repository.findAll());
    }
}
