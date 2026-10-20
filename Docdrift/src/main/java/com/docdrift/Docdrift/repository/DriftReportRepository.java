package com.docdrift.Docdrift.repository;
import com.docdrift.Docdrift.model.DriftReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DriftReportRepository extends JpaRepository<DriftReport, Long>, JpaSpecificationExecutor<DriftReport> {
    boolean existsByRepositoryNameIgnoreCaseAndCommitShaAndPullRequestNumberIsNull(String repositoryName, String commitSha);
}
