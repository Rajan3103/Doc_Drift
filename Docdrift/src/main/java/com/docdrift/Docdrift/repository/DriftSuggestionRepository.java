package com.docdrift.Docdrift.repository;
import com.docdrift.Docdrift.model.DriftSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriftSuggestionRepository extends JpaRepository<DriftSuggestion, Long> {
    boolean existsByStatusAndReport_RepositoryNameIgnoreCase(String status, String repositoryName);
}
