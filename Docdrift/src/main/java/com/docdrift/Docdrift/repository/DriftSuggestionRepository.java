package com.docdrift.Docdrift.repository;
import com.docdrift.Docdrift.model.DriftSuggestion;
import com.docdrift.Docdrift.model.Severity;
import com.docdrift.Docdrift.model.SuggestionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriftSuggestionRepository extends JpaRepository<DriftSuggestion, Long> {
    boolean existsByStatusAndReport_RepositoryNameIgnoreCase(SuggestionStatus status, String repositoryName);
    long countByStatus(SuggestionStatus status);
    long countByStatusAndSeverity(SuggestionStatus status, Severity severity);
    long countByStatusAndIsBreakingChangeTrue(SuggestionStatus status);
}
