package com.docdrift.Docdrift.repository;
import com.docdrift.Docdrift.model.RepositoryConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositoryConfigRepository extends JpaRepository<RepositoryConfig, Long> {
    RepositoryConfig findByUrl(String url);
}
