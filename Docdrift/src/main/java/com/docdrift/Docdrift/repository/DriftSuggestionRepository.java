package com.docdrift.Docdrift.repository;
import com.docdrift.Docdrift.model.DriftSuggestion;
import com.docdrift.Docdrift.model.Severity;
import com.docdrift.Docdrift.model.SuggestionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DriftSuggestionRepository extends JpaRepository<DriftSuggestion, Long> {
    // Pull request checks don't count: the watched branch itself has not drifted until the PR is merged
    boolean existsByStatusAndReport_RepositoryNameIgnoreCaseAndReport_PullRequestNumberIsNull(SuggestionStatus status, String repositoryName);
    long countByStatus(SuggestionStatus status);

    /** [analyzedAt, severity] of every suggestion found on the repository's watched branches since a date. */
    @Query("select r.analyzedAt, s.severity from DriftSuggestion s join s.report r "
            + "where lower(r.repositoryName) = lower(:name) and r.analyzedAt >= :since and r.pullRequestNumber is null")
    List<Object[]> findSeveritiesSince(@Param("name") String repositoryName, @Param("since") LocalDateTime since);
    long countByStatusAndSeverity(SuggestionStatus status, Severity severity);
    long countByStatusAndIsBreakingChangeTrue(SuggestionStatus status);
}
