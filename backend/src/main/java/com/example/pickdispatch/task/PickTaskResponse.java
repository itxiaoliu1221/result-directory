package com.example.pickdispatch.task;

import java.time.LocalDateTime;

public record PickTaskResponse(
        Long id,
        String taskNo,
        Long orderId,
        String orderNo,
        Long warehouseId,
        String warehouseName,
        Long goodsId,
        String goodsName,
        String category,
        Integer requiredQty,
        TaskStatus status,
        String reason,
        Integer snapshotQty,
        LocalDateTime snapshotSyncedAt,
        Long snapshotAgeMinutes,
        String claimedBy,
        LocalDateTime claimedAt,
        LocalDateTime createdAt,
        LocalDateTime dispatchedAt,
        LocalDateTime completedAt,
        TaskResult result,
        String requestNo
) {
}
