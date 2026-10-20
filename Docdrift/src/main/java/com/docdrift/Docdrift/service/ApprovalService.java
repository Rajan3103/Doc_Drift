package com.docdrift.Docdrift.service;

import com.docdrift.Docdrift.model.DriftReport;
import com.docdrift.Docdrift.model.DriftSuggestion;
import com.docdrift.Docdrift.model.RepositoryStatus;
import com.docdrift.Docdrift.model.SuggestionStatus;
import com.docdrift.Docdrift.repository.DriftSuggestionRepository;
import com.docdrift.Docdrift.repository.RepositoryConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Approves suggestions. Suggestions with a text replacement are applied together: in one new pull request,
 * or, for suggestions from a pull request check, as a commit on that pull request's branch.
 * Suggestions without one (rule-based findings) are simply marked as approved.
 */
@Service
public class ApprovalService {

    private static final Duration GITHUB_TIMEOUT = Duration.ofSeconds(60);
    public static final int MAX_BATCH = 50;

    private final DriftSuggestionRepository suggestionRepository;
    private final RepositoryConfigRepository repositoryConfigRepository;
    private final GitHubService gitHubService;
    private final DiffAnalyzerService diffAnalyzerService;

    public ApprovalService(DriftSuggestionRepository suggestionRepository,
                           RepositoryConfigRepository repositoryConfigRepository,
                           GitHubService gitHubService,
                           DiffAnalyzerService diffAnalyzerService) {
        this.suggestionRepository = suggestionRepository;
        this.repositoryConfigRepository = repositoryConfigRepository;
        this.gitHubService = gitHubService;
        this.diffAnalyzerService = diffAnalyzerService;
    }

    /** A request that cannot be completed, with the HTTP status to report. */
    public static class ApprovalException extends RuntimeException {
        private final HttpStatus status;

        public ApprovalException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        public HttpStatus getStatus() {
            return status;
        }
    }

    public record ApprovalResult(List<DriftSuggestion> approved, String prUrl) {
    }

    public ApprovalResult approve(List<Long> ids) {
        List<Long> distinctIds = ids.stream().distinct().toList();
        if (distinctIds.isEmpty() || distinctIds.size() > MAX_BATCH) {
            throw new ApprovalException(HttpStatus.BAD_REQUEST, "Select between 1 and " + MAX_BATCH + " suggestions.");
        }
        List<DriftSuggestion> suggestions = new ArrayList<>();
        for (Long id : distinctIds) {
            suggestions.add(suggestionRepository.findById(id).orElseThrow(() ->
                    new ApprovalException(HttpStatus.NOT_FOUND, "Suggestion " + id + " does not exist.")));
        }
        for (DriftSuggestion s : suggestions) {
            if (s.getStatus() != SuggestionStatus.PENDING) {
                throw new ApprovalException(HttpStatus.CONFLICT, "Suggestion " + s.getId() + " is already " + s.getStatus() + ".");
            }
        }

        DriftReport first = suggestions.getFirst().getReport();
        String repoName = first.getRepositoryName();
        for (DriftSuggestion s : suggestions) {
            if (!s.getReport().getRepositoryName().equalsIgnoreCase(repoName)) {
                throw new ApprovalException(HttpStatus.BAD_REQUEST, "All selected suggestions must belong to the same repository.");
            }
            if (!Objects.equals(s.getReport().getPullRequestNumber(), first.getPullRequestNumber())) {
                throw new ApprovalException(HttpStatus.BAD_REQUEST,
                        "Suggestions from a pull request check can only be combined with others from the same pull request.");
            }
        }

        List<DriftSuggestion> applicable = suggestions.stream().filter(ApprovalService::hasReplacement).toList();
        String prUrl = applicable.isEmpty() ? null : apply(repoName, first, applicable);

        for (DriftSuggestion s : suggestions) {
            s.setStatus(SuggestionStatus.APPROVED);
            if (hasReplacement(s)) {
                s.setPrUrl(prUrl);
            }
        }
        List<DriftSuggestion> saved = suggestionRepository.saveAll(suggestions);
        refreshRepositoryStatus(repoName);
        return new ApprovalResult(saved, prUrl);
    }

    private String apply(String repoName, DriftReport report, List<DriftSuggestion> applicable) {
        List<GitHubService.TextEdit> edits = applicable.stream()
                .map(s -> new GitHubService.TextEdit(s.getFilePath(), s.getOldText(), s.getSuggestedText()))
                .toList();
        String title = applicable.size() == 1
                ? "docs: fix drift in " + applicable.getFirst().getFilePath()
                : "docs: fix " + applicable.size() + " documentation drift issues";

        try {
            if (report.getPullRequestNumber() != null) {
                if (report.getHeadBranch() == null) {
                    throw new ApprovalException(HttpStatus.CONFLICT, "Pull request #" + report.getPullRequestNumber()
                            + " comes from a fork, so DocDrift cannot commit to it. Apply the fix in the pull request manually.");
                }
                gitHubService.commitDocFixes(repoName, report.getHeadBranch(), edits, title).block(GITHUB_TIMEOUT);
                return "https://github.com/" + repoName + "/pull/" + report.getPullRequestNumber();
            }
            String branchName = "docdrift/fix-" + applicable.getFirst().getId() + "-" + Instant.now().getEpochSecond();
            return gitHubService.createDocFixPullRequest(repoName, edits, branchName, title, body(applicable))
                    .block(GITHUB_TIMEOUT);
        } catch (ApprovalException e) {
            throw e;
        } catch (IllegalStateException e) {
            // The outdated text no longer exists in the file
            throw new ApprovalException(HttpStatus.CONFLICT, e.getMessage());
        } catch (WebClientResponseException e) {
            throw new ApprovalException(HttpStatus.BAD_GATEWAY, "GitHub rejected the request (" + e.getStatusCode().value()
                    + "). Check that the token has contents and pull request write access.");
        } catch (RuntimeException e) {
            throw new ApprovalException(HttpStatus.BAD_GATEWAY, "Could not apply the fix on GitHub: " + e.getMessage());
        }
    }

    private static String body(List<DriftSuggestion> applicable) {
        StringBuilder body = new StringBuilder("Automated documentation fixes approved in DocDrift.\n");
        for (DriftSuggestion s : applicable) {
            body.append("\n### ").append(s.getFilePath()).append(" - ").append(s.getImpactedSymbol()).append('\n')
                    .append("**Drift type:** ").append(s.getDriftType())
                    .append(" | **Severity:** ").append(s.getSeverity())
                    .append(" | **Triggered by commit:** ").append(s.getReport().getCommitSha()).append('\n')
                    .append(s.getReason() != null ? s.getReason() : "").append('\n');
        }
        return body.toString();
    }

    private static boolean hasReplacement(DriftSuggestion s) {
        return s.getOldText() != null && !s.getOldText().isEmpty() && s.getSuggestedText() != null;
    }

    /** A repository stops being DRIFTED once all of its suggestions have been reviewed. */
    public void refreshRepositoryStatus(String repositoryName) {
        repositoryConfigRepository.findFirstByNameIgnoreCase(repositoryName).ifPresent(config -> {
            if (config.getStatus() != RepositoryStatus.SCANNING && config.getStatus() != RepositoryStatus.ERROR) {
                config.setStatus(diffAnalyzerService.statusFromPendingSuggestions(repositoryName));
                repositoryConfigRepository.save(config);
            }
        });
    }
}
