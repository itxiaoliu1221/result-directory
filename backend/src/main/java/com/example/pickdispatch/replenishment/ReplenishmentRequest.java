package com.example.pickdispatch.replenishment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReplenishmentRequest(
        @Size(max = 64, message = "长度不能超过 64") String requestNo,
        @NotNull(message = "不能为空") Long warehouseId,
        @Size(max = 32, message = "长度不能超过 32") String source,
        @NotEmpty(message = "至少一条明细") List<@Valid ReplenishmentItemRequest> items
) {
}
