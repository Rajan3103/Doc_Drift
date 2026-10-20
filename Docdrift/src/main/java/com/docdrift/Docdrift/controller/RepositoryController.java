package com.docdrift.Docdrift.controller;

import com.docdrift.Docdrift.dto.CreateRepositoryRequest;
import com.docdrift.Docdrift.dto.DailyDriftCount;
import com.docdrift.Docdrift.dto.PageResponse;
import com.docdrift.Docdrift.dto.RepositorySettingsRequest;
import com.docdrift.Docdrift.model.RepositoryConfig;
import com.docdrift.Docdrift.model.RepositoryStatus;
import com.docdrift.Docdrift.model.Severity;
import com.docdrift.Docdrift.repository.DriftSuggestionRepository;
import com.docdrift.Docdrift.repository.RepositoryConfigRepository;
import com.docdrift.Docdrift.service.DiffAnalyzerService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@RestController
@RequestMapping("/api/repos")
public class RepositoryController {

    private final RepositoryConfigRepository repository;
    private final DriftSuggestionRepository suggestionRepository;
    private final DiffAnalyzerService diffAnalyzerService;

    public RepositoryController(RepositoryConfigRepository repository, DriftSuggestionRepository suggestionRepository,
                                DiffAnalyzerService diffAnalyzerService) {
        this.repository = repository;
        this.suggestionRepository = suggestionRepository;
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

    @PutMapping("/{id}/settings")
    public ResponseEntity<?> updateSettings(@PathVariable Long id, @Valid @RequestBody RepositorySettingsRequest request) {
        RepositoryConfig config = repository.findById(id).orElse(null);
        if (config == null) {
            return ResponseEntity.notFound().build();
        }
        if (request.docPaths().stream().anyMatch(path -> path.contains(".."))) {
            return ResponseEntity.badRequest().body(Map.of("error", "docPaths must not contain '..'."));
        }

        config.setDocPaths(request.docPaths().stream().map(String::trim).distinct().toList());
        config.setWatchedBranches(request.watchedBranches().stream().map(String::trim).distinct().toList());
        config.setMinSeverity(request.minSeverity());
        config.setMinConfidence(request.minConfidence());
        config.setAnalyzePullRequests(request.analyzePullRequests());
        if (request.slackWebhookUrl() != null) {
            config.setSlackWebhookUrl(request.slackWebhookUrl().isBlank() ? null : request.slackWebhookUrl());
        }
        config.setAlertEmail(request.alertEmail() == null || request.alertEmail().isBlank() ? null : request.alertEmail().trim());
        return ResponseEntity.ok(repository.save(config));
    }

    /** Suggestions found per day over the last N days, for the dashboard's drift chart. */
    @GetMapping("/{id}/history")
    public ResponseEntity<?> getHistory(@PathVariable Long id, @RequestParam(defaultValue = "30") int days) {
        RepositoryConfig config = repository.findById(id).orElse(null);
        if (config == null) {
            return ResponseEntity.notFound().build();
        }
        int window = Math.clamp(days, 1, 365);
        LocalDate today = LocalDate.now();
        LocalDate first = today.minusDays(window - 1L);

        Map<LocalDate, long[]> counts = new TreeMap<>();
        for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            counts.put(day, new long[Severity.values().length]);
        }
        for (Object[] row : suggestionRepository.findSeveritiesSince(config.getName(), first.atStartOfDay())) {
            long[] day = counts.get(((LocalDateTime) row[0]).toLocalDate());
            if (day != null && row[1] instanceof Severity severity) {
                day[severity.ordinal()]++;
            }
        }
        List<DailyDriftCount> history = counts.entrySet().stream()
                .map(e -> new DailyDriftCount(e.getKey(), e.getValue()[Severity.CRITICAL.ordinal()],
                        e.getValue()[Severity.HIGH.ordinal()], e.getValue()[Severity.MEDIUM.ordinal()],
                        e.getValue()[Severity.LOW.ordinal()]))
                .toList();
        return ResponseEntity.ok(history);
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
