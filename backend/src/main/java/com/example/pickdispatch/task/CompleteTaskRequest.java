package com.example.pickdispatch.task;

import jakarta.validation.constraints.NotNull;

public record CompleteTaskRequest(
        @NotNull(message = "不能为空") TaskResult result
) {
}
