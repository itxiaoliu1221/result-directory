package com.example.pickdispatch.replenishment;

import com.example.pickdispatch.task.PickTaskResponse;

import java.util.List;

public record ReplenishmentResponse(
        Long id,
        String requestNo,
        String warehouseName,
        List<ItemResult> items,
        List<PickTaskResponse> dispatchedTasks
) {
    public record ItemResult(
            Long goodsId,
            String goodsName,
            Integer replenishedQty,
            Integer remainingQty,
            Integer dispatchedCount
    ) {
    }
}
