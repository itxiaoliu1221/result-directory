package com.example.pickdispatch.order;

import com.example.pickdispatch.task.TaskStatus;

import java.time.LocalDateTime;

public record OrderResponse(
        Long id,
        String orderNo,
        String customerName,
        Long warehouseId,
        String warehouseName,
        Long goodsId,
        String goodsName,
        Integer quantity,
        OrderStatus status,
        Long taskId,
        String taskNo,
        TaskStatus taskStatus,
        String claimedBy,
        LocalDateTime createdAt,
        LocalDateTime dispatchedAt,
        LocalDateTime claimedAt,
        LocalDateTime completedAt
) {
}
