package com.example.pickdispatch.replenishment;

import java.time.LocalDateTime;
import java.util.List;

public record ReplenishmentHistoryResponse(
        Long id,
        String requestNo,
        Long warehouseId,
        String warehouseName,
        String source,
        LocalDateTime createdAt,
        List<Item> items
) {
    public record Item(Long goodsId, String goodsName, Integer quantity) {
    }
}
