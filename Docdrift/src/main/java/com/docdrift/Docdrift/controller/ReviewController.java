package com.docdrift.Docdrift.controller;

import com.docdrift.Docdrift.dto.ApproveBatchRequest;
import com.docdrift.Docdrift.dto.EditSuggestionRequest;
import com.docdrift.Docdrift.dto.PageResponse;
import com.docdrift.Docdrift.dto.ReportView;
import com.docdrift.Docdrift.dto.ReviewSummary;
import com.docdrift.Docdrift.model.DriftReport;
import com.docdrift.Docdrift.model.DriftSuggestion;
import com.docdrift.Docdrift.model.Severity;
import com.docdrift.Docdrift.model.SuggestionStatus;
import com.docdrift.Docdrift.repository.DriftReportRepository;
import com.docdrift.Docdrift.repository.DriftSuggestionRepository;
import com.docdrift.Docdrift.service.ApprovalService;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    private final ApprovalService approvalService;

    public ReviewController(DriftReportRepository reportRepository,
                            DriftSuggestionRepository suggestionRepository,
                            ApprovalService approvalService) {
        this.reportRepository = reportRepository;
        this.suggestionRepository = suggestionRepository;
        this.approvalService = approvalService;
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
                report.getPullRequestNumber(), report.getHeadBranch(),
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
        try {
            return ResponseEntity.ok(approvalService.approve(List.of(id)).approved().getFirst());
        } catch (ApprovalService.ApprovalException e) {
            return error(e.getStatus(), e.getMessage());
        }
    }

    /** Approves several suggestions from one repository; their fixes go into a single pull request. */
    @PostMapping("/suggestions/approve")
    public ResponseEntity<?> approveSuggestions(@Valid @RequestBody ApproveBatchRequest request) {
        try {
            return ResponseEntity.ok(approvalService.approve(request.ids()));
        } catch (ApprovalService.ApprovalException e) {
            return error(e.getStatus(), e.getMessage());
        }
    }

    /** Lets a reviewer change the suggested text before approving. The AI's original text is kept. */
    @PatchMapping("/suggestions/{id}")
    public ResponseEntity<?> editSuggestion(@PathVariable Long id, @Valid @RequestBody EditSuggestionRequest request) {
        DriftSuggestion suggestion = suggestionRepository.findById(id).orElse(null);
        if (suggestion == null) {
            return ResponseEntity.notFound().build();
        }
        if (suggestion.getStatus() != SuggestionStatus.PENDING) {
            return error(HttpStatus.CONFLICT, "Only pending suggestions can be edited.");
        }
        if (suggestion.getOriginalSuggestedText() == null) {
            suggestion.setOriginalSuggestedText(suggestion.getSuggestedText());
        }
        suggestion.setSuggestedText(request.suggestedText());
        return ResponseEntity.ok(suggestionRepository.save(suggestion));
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    @PostMapping("/suggestions/{id}/reject")
    public ResponseEntity<DriftSuggestion> rejectSuggestion(@PathVariable Long id) {
        return suggestionRepository.findById(id).map(suggestion -> {
            suggestion.setStatus(SuggestionStatus.REJECTED);
            DriftSuggestion saved = suggestionRepository.save(suggestion);
            approvalService.refreshRepositoryStatus(suggestion.getReport().getRepositoryName());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }
}
