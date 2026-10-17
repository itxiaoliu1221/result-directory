package com.example.pickdispatch.task;

import java.time.LocalDateTime;

public record TaskCompletedEvent(
        Long taskId,
        String taskNo,
        Long orderId,
        String orderNo,
        TaskResult result,
        LocalDateTime completedAt
) {
}
