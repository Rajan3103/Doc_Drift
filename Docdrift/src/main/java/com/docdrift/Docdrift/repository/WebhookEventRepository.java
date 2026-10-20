package com.docdrift.Docdrift.repository;
import com.docdrift.Docdrift.model.EventStatus;
import com.docdrift.Docdrift.model.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    boolean existsByDeliveryId(String deliveryId);
    List<WebhookEvent> findByStatus(EventStatus status);
}
