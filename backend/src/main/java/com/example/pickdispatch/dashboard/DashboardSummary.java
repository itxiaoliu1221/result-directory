package com.example.pickdispatch.dashboard;

import com.example.pickdispatch.task.PickTaskResponse;

import java.util.List;

public record DashboardSummary(
        long totalTasks,
        long pending,
        long dispatched,
        long fulfilled,
        long stockout,
        long inventoryRows,
        long staleInventory,
        List<PickTaskResponse> recentPending
) {
}
