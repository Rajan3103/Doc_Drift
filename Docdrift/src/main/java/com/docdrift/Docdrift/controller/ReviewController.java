package com.docdrift.Docdrift.controller;

import com.docdrift.Docdrift.dto.PageResponse;
import com.docdrift.Docdrift.dto.ReportView;
import com.docdrift.Docdrift.dto.ReviewSummary;
import com.docdrift.Docdrift.model.DriftReport;
import com.docdrift.Docdrift.model.DriftSuggestion;
import com.docdrift.Docdrift.model.RepositoryStatus;
import com.docdrift.Docdrift.model.Severity;
import com.docdrift.Docdrift.model.SuggestionStatus;
import com.docdrift.Docdrift.repository.DriftReportRepository;
import com.docdrift.Docdrift.repository.DriftSuggestionRepository;
import com.docdrift.Docdrift.repository.RepositoryConfigRepository;
import com.docdrift.Docdrift.service.DiffAnalyzerService;
import com.docdrift.Docdrift.service.GitHubService;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final DriftReportRepository reportRepository;
    private final DriftSuggestionRepository suggestionRepository;
    private final GitHubService gitHubService;
    private final DiffAnalyzerService diffAnalyzerService;
    private final RepositoryConfigRepository repositoryConfigRepository;

    private static final Duration GITHUB_TIMEOUT = Duration.ofSeconds(30);

    public ReviewController(DriftReportRepository reportRepository,
                            DriftSuggestionRepository suggestionRepository,
                            GitHubService gitHubService,
                            DiffAnalyzerService diffAnalyzerService,
                            RepositoryConfigRepository repositoryConfigRepository) {
        this.reportRepository = reportRepository;
        this.suggestionRepository = suggestionRepository;
        this.gitHubService = gitHubService;
        this.diffAnalyzerService = diffAnalyzerService;
        this.repositoryConfigRepository = repositoryConfigRepository;
    }

    /** Newest reports first; severity/status filters keep reports with at least one matching suggestion. */
    @GetMapping("/reports")
    public PageResponse<ReportView> getReports(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) String repository,
                                               @RequestParam(required = false) Severity severity,
                                               @RequestParam(required = false) SuggestionStatus status) {
        Specification<DriftReport> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (repository != null && !repository.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("repositoryName")), repository.trim().toLowerCase(Locale.ROOT)));
            }
            if (severity != null || status != null) {
                Subquery<Long> matching = query.subquery(Long.class);
                Root<DriftSuggestion> suggestion = matching.from(DriftSuggestion.class);
                List<Predicate> conditions = new ArrayList<>(List.of(cb.equal(suggestion.get("report"), root)));
                if (severity != null) conditions.add(cb.equal(suggestion.get("severity"), severity));
                if (status != null) conditions.add(cb.equal(suggestion.get("status"), status));
                matching.select(suggestion.get("id")).where(conditions.toArray(Predicate[]::new));
                predicates.add(cb.exists(matching));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by(Sort.Direction.DESC, "analyzedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.from(reportRepository.findAll(spec, pageRequest).map(report -> new ReportView(
                report.getId(), report.getCommitSha(), report.getRepositoryName(), report.getAnalyzedAt(),
                report.getSuggestions() == null ? List.of() : report.getSuggestions().stream()
                        .filter(s -> severity == null || s.getSeverity() == severity)
                        .filter(s -> status == null || s.getStatus() == status)
                        .sorted(Comparator.comparing(DriftSuggestion::getId))
                        .toList())));
    }

    @GetMapping("/reports/summary")
    public ReviewSummary getSummary() {
        return new ReviewSummary(
                suggestionRepository.countByStatus(SuggestionStatus.PENDING),
                suggestionRepository.countByStatusAndSeverity(SuggestionStatus.PENDING, Severity.CRITICAL),
                suggestionRepository.countByStatusAndIsBreakingChangeTrue(SuggestionStatus.PENDING));
    }

    @PostMapping("/suggestions/{id}/approve")
    public ResponseEntity<?> approveSuggestion(@PathVariable Long id) {
        DriftSuggestion suggestion = suggestionRepository.findById(id).orElse(null);
        if (suggestion == null) {
            return ResponseEntity.notFound().build();
        }
        if (suggestion.getStatus() != SuggestionStatus.PENDING) {
            return error(HttpStatus.CONFLICT, "Suggestion is already " + suggestion.getStatus() + ".");
        }
        if (suggestion.getOldText() == null || suggestion.getOldText().isEmpty() || suggestion.getSuggestedText() == null) {
            return error(HttpStatus.UNPROCESSABLE_CONTENT, "Suggestion has no text replacement to apply.");
        }

        DriftReport report = suggestion.getReport();
        String branchName = "docdrift/suggestion-" + suggestion.getId() + "-" + Instant.now().getEpochSecond();
        String title = "docs: fix drift in " + suggestion.getFilePath();
        String body = "Automated documentation fix approved in DocDrift.\n\n"
                + "**Drift type:** " + suggestion.getDriftType() + "\n"
                + "**Severity:** " + suggestion.getSeverity() + "\n"
                + "**Triggered by commit:** " + report.getCommitSha() + "\n\n"
                + "**Reason:** " + suggestion.getReason();

        try {
            String prUrl = gitHubService.createDocFixPullRequest(report.getRepositoryName(), suggestion.getFilePath(),
                            suggestion.getOldText(), suggestion.getSuggestedText(), branchName, title, body)
                    .block(GITHUB_TIMEOUT);
            suggestion.setPrUrl(prUrl);
        } catch (IllegalStateException e) {
            // The outdated text no longer exists in the file
            return error(HttpStatus.CONFLICT, e.getMessage());
        } catch (WebClientResponseException e) {
            return error(HttpStatus.BAD_GATEWAY, "GitHub rejected the request (" + e.getStatusCode().value()
                    + "). Check that the token has contents and pull request write access.");
        } catch (RuntimeException e) {
            return error(HttpStatus.BAD_GATEWAY, "Could not open a pull request: " + e.getMessage());
        }

        // Only mark as approved once the pull request actually exists
        suggestion.setStatus(SuggestionStatus.APPROVED);
        DriftSuggestion saved = suggestionRepository.save(suggestion);
        refreshRepositoryStatus(report.getRepositoryName());
        return ResponseEntity.ok(saved);
    }

    /** A repository stops being DRIFTED once all of its suggestions have been reviewed. */
    private void refreshRepositoryStatus(String repositoryName) {
        repositoryConfigRepository.findFirstByNameIgnoreCase(repositoryName).ifPresent(config -> {
            if (config.getStatus() != RepositoryStatus.SCANNING && config.getStatus() != RepositoryStatus.ERROR) {
                config.setStatus(diffAnalyzerService.statusFromPendingSuggestions(repositoryName));
                repositoryConfigRepository.save(config);
            }
        });
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    @PostMapping("/suggestions/{id}/reject")
    public ResponseEntity<DriftSuggestion> rejectSuggestion(@PathVariable Long id) {
        return suggestionRepository.findById(id).map(suggestion -> {
            suggestion.setStatus(SuggestionStatus.REJECTED);
            DriftSuggestion saved = suggestionRepository.save(suggestion);
            refreshRepositoryStatus(suggestion.getReport().getRepositoryName());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }
}
