package com.example.pickdispatch.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    List<NotificationLog> findByTaskIdOrderByCreatedAtDesc(Long taskId);

    long countByTaskId(Long taskId);
}
