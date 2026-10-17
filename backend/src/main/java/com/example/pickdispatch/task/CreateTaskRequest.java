package com.example.pickdispatch.task;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTaskRequest(
        @NotNull(message = "不能为空") Long warehouseId,
        @NotNull(message = "不能为空") Long goodsId,
        @NotNull(message = "不能为空") @Min(value = 1, message = "必须大于 0") Integer requiredQty,
        @Size(max = 64, message = "长度不能超过 64") String requestNo
) {
}
