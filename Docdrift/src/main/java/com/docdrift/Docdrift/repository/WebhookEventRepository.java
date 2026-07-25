package com.docdrift.Docdrift.repository;
import com.docdrift.Docdrift.model.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
}
