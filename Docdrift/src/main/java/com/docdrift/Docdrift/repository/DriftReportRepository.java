package com.docdrift.Docdrift.repository;
import com.docdrift.Docdrift.model.DriftReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriftReportRepository extends JpaRepository<DriftReport, Long> {
}
