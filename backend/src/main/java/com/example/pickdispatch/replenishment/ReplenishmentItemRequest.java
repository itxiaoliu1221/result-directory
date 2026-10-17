package com.example.pickdispatch.replenishment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReplenishmentItemRequest(
        @NotNull(message = "不能为空") Long goodsId,
        @NotNull(message = "不能为空") @Min(value = 1, message = "必须大于 0") Integer quantity
) {
}
