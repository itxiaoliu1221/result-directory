package com.example.pickdispatch.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClaimTaskRequest(
        @NotBlank(message = "不能为空") @Size(max = 64, message = "长度不能超过 64") String pickerName
) {
}
