package com.example.pickdispatch.task;

import com.example.pickdispatch.common.ApiResponse;
import com.example.pickdispatch.common.PageResponse;
import com.example.pickdispatch.order.ClaimTaskRequest;
import com.example.pickdispatch.order.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pick-tasks")
@RequiredArgsConstructor
public class PickTaskController {

    private final TaskService taskService;
    private final OrderService orderService;

    @PostMapping
    public ApiResponse<PickTaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        return ApiResponse.ok(taskService.createTask(request));
    }

    @GetMapping
    public ApiResponse<PageResponse<PickTaskResponse>> list(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long goodsId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(taskService.list(status, warehouseId, goodsId, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<PickTaskResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(taskService.getTask(id));
    }

    @PostMapping("/{id}/claim")
    public ApiResponse<PickTaskResponse> claim(
            @PathVariable Long id,
            @Valid @RequestBody ClaimTaskRequest request) {
        orderService.claimTask(id, request.pickerName());
        return ApiResponse.ok(taskService.getTask(id));
    }

    @PostMapping("/{id}/result")
    public ApiResponse<PickTaskResponse> complete(
            @PathVariable Long id,
            @Valid @RequestBody CompleteTaskRequest request) {
        return ApiResponse.ok(taskService.completeTask(id, request.result()));
    }
}
