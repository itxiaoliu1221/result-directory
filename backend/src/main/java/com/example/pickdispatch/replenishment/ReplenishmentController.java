package com.example.pickdispatch.replenishment;

import com.example.pickdispatch.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/replenishments")
@RequiredArgsConstructor
public class ReplenishmentController {

    private final ReplenishmentService replenishmentService;

    @PostMapping
    public ApiResponse<ReplenishmentResponse> replenish(
            @Valid @RequestBody ReplenishmentRequest request) {
        return ApiResponse.ok(replenishmentService.replenish(request));
    }

    @GetMapping
    public ApiResponse<List<ReplenishmentHistoryResponse>> history() {
        return ApiResponse.ok(replenishmentService.history());
    }
}
