package com.example.pickdispatch.inventory;

import java.time.LocalDateTime;

public record InventoryResponse(
        Long id,
        Long warehouseId,
        String warehouseName,
        Long goodsId,
        String goodsCode,
        String goodsName,
        String category,
        Integer availableQty,
        LocalDateTime syncedAt,
        long freshnessMinutes,
        boolean stale
) {
}
