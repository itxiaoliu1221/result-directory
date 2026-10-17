package com.example.pickdispatch.dashboard;

import com.example.pickdispatch.inventory.InventoryRepository;
import com.example.pickdispatch.inventory.InventoryService;
import com.example.pickdispatch.task.PickTaskRepository;
import com.example.pickdispatch.task.TaskService;
import com.example.pickdispatch.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final PickTaskRepository pickTaskRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;
    private final TaskService taskService;

    @Transactional(readOnly = true)
    public DashboardSummary summary() {
        return new DashboardSummary(
                pickTaskRepository.count(),
                pickTaskRepository.countByStatus(TaskStatus.PENDING),
                pickTaskRepository.countByStatus(TaskStatus.DISPATCHED),
                pickTaskRepository.countByStatus(TaskStatus.FULFILLED),
                pickTaskRepository.countByStatus(TaskStatus.STOCKOUT),
                inventoryRepository.count(),
                inventoryService.countStale(),
                taskService.recentPending()
        );
    }
}
