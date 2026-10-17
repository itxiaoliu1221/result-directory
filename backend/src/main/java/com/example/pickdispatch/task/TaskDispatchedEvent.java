package com.example.pickdispatch.task;

import java.time.LocalDateTime;

public record TaskDispatchedEvent(
        Long taskId,
        String taskNo,
        Long orderId,
        String orderNo,
        Long warehouseId,
        String warehouseName,
        Long goodsId,
        String goodsName,
        Integer requiredQty,
        LocalDateTime dispatchedAt
) {
}
