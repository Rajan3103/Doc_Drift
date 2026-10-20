package com.docdrift.Docdrift.repository;
import com.docdrift.Docdrift.model.RepositoryConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RepositoryConfigRepository extends JpaRepository<RepositoryConfig, Long> {
    RepositoryConfig findByUrl(String url);
    boolean existsByNameIgnoreCase(String name);
    Optional<RepositoryConfig> findFirstByNameIgnoreCase(String name);
    List<RepositoryConfig> findByStatus(String status);
}
