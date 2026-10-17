package com.example.pickdispatch.task;

import java.time.LocalDateTime;

public record TaskClaimedEvent(
        Long taskId,
        String taskNo,
        Long orderId,
        String orderNo,
        String pickerName,
        LocalDateTime claimedAt
) {
}
