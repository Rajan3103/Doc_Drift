package com.docdrift.Docdrift.controller;

import com.docdrift.Docdrift.model.DriftReport;
import com.docdrift.Docdrift.model.DriftSuggestion;
import com.docdrift.Docdrift.repository.DriftReportRepository;
import com.docdrift.Docdrift.repository.DriftSuggestionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final DriftReportRepository reportRepository;
    private final DriftSuggestionRepository suggestionRepository;

    public ReviewController(DriftReportRepository reportRepository, DriftSuggestionRepository suggestionRepository) {
        this.reportRepository = reportRepository;
        this.suggestionRepository = suggestionRepository;
    }

    @GetMapping("/reports")
    public ResponseEntity<List<DriftReport>> getAllReports() {
        return ResponseEntity.ok(reportRepository.findAll());
    }

    @PostMapping("/suggestions/{id}/approve")
    public ResponseEntity<DriftSuggestion> approveSuggestion(@PathVariable Long id) {
        return suggestionRepository.findById(id).map(suggestion -> {
            suggestion.setStatus("APPROVED");
            // In full implementation, this would call GitHubService to open a PR
            DriftSuggestion saved = suggestionRepository.save(suggestion);
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping("/suggestions/{id}/reject")
    public ResponseEntity<DriftSuggestion> rejectSuggestion(@PathVariable Long id) {
        return suggestionRepository.findById(id).map(suggestion -> {
            suggestion.setStatus("REJECTED");
            DriftSuggestion saved = suggestionRepository.save(suggestion);
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }
}
