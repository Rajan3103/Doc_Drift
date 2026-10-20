package com.docdrift.Docdrift.dto;

import com.docdrift.Docdrift.model.DriftSuggestion;

import java.time.LocalDateTime;
import java.util.List;

/** A drift report with only the suggestions that match the requested filters. */
public record ReportView(Long id, String commitSha, String repositoryName, LocalDateTime analyzedAt,
                         Integer pullRequestNumber, String headBranch, List<DriftSuggestion> suggestions) {
}
