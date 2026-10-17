package com.example.pickdispatch;

import com.example.pickdispatch.common.BusinessException;
import com.example.pickdispatch.goods.Goods;
import com.example.pickdispatch.goods.GoodsRepository;
import com.example.pickdispatch.inventory.Inventory;
import com.example.pickdispatch.inventory.InventoryRepository;
import com.example.pickdispatch.inventory.InventorySnapshotRepository;
import com.example.pickdispatch.notification.NotificationLogRepository;
import com.example.pickdispatch.replenishment.ReplenishmentItemRequest;
import com.example.pickdispatch.replenishment.ReplenishmentRepository;
import com.example.pickdispatch.replenishment.ReplenishmentRequest;
import com.example.pickdispatch.replenishment.ReplenishmentService;
import com.example.pickdispatch.task.CreateTaskRequest;
import com.example.pickdispatch.task.PickTask;
import com.example.pickdispatch.task.PickTaskRepository;
import com.example.pickdispatch.task.PickTaskResponse;
import com.example.pickdispatch.task.TaskResult;
import com.example.pickdispatch.task.TaskService;
import com.example.pickdispatch.task.TaskStatus;
import com.example.pickdispatch.warehouse.Warehouse;
import com.example.pickdispatch.warehouse.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class TaskDispatchFlowTest {

    @Autowired
    private TaskService taskService;
    @Autowired
    private ReplenishmentService replenishmentService;
    @Autowired
    private WarehouseRepository warehouseRepository;
    @Autowired
    private GoodsRepository goodsRepository;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private InventorySnapshotRepository snapshotRepository;
    @Autowired
    private PickTaskRepository pickTaskRepository;
    @Autowired
    private ReplenishmentRepository replenishmentRepository;
    @Autowired
    private NotificationLogRepository notificationLogRepository;

    private Warehouse warehouse;
    private Goods goods;

    @BeforeEach
    void setUp() {
        notificationLogRepository.deleteAll();
        pickTaskRepository.deleteAll();
        snapshotRepository.deleteAll();
        inventoryRepository.deleteAll();
        replenishmentRepository.deleteAll();
        goodsRepository.deleteAll();
        warehouseRepository.deleteAll();

        warehouse = new Warehouse();
        warehouse.setCode("W1");
        warehouse.setName("测试仓");
        warehouse = warehouseRepository.save(warehouse);

        goods = new Goods();
        goods.setCode("G1");
        goods.setName("测试货物");
        goods.setCategory("测试品类");
        goods = goodsRepository.save(goods);
    }

    @Test
    void dispatchesImmediatelyWhenStockIsEnough() {
        stock(10, LocalDateTime.now());

        PickTaskResponse response = taskService.createTask(
                new CreateTaskRequest(warehouse.getId(), goods.getId(), 3, "REQ-1"));

        assertEquals(TaskStatus.DISPATCHED, response.status());
        assertEquals(7, currentStock());
        assertEquals(1, notificationLogRepository.countByTaskId(response.id()));
    }

    @Test
    void staysPendingWhenStockIsInsufficient() {
        stock(2, LocalDateTime.now());

        PickTaskResponse response = taskService.createTask(
                new CreateTaskRequest(warehouse.getId(), goods.getId(), 5, "REQ-2"));

        assertEquals(TaskStatus.PENDING, response.status());
        assertEquals("INSUFFICIENT_STOCK", response.reason());
        assertEquals(2, currentStock());
        assertEquals(0, notificationLogRepository.countByTaskId(response.id()));
    }

    @Test
    void staysPendingWhenSnapshotIsStale() {
        stock(10, LocalDateTime.now().minusMinutes(30));

        PickTaskResponse response = taskService.createTask(
                new CreateTaskRequest(warehouse.getId(), goods.getId(), 3, "REQ-3"));

        assertEquals(TaskStatus.PENDING, response.status());
        assertEquals("SNAPSHOT_STALE", response.reason());
        assertEquals(10, currentStock());
    }

    @Test
    void replenishmentDispatchesPendingTasksInFifoOrder() {
        stock(0, LocalDateTime.now());
        PickTaskResponse first = taskService.createTask(
                new CreateTaskRequest(warehouse.getId(), goods.getId(), 5, "REQ-F1"));
        PickTaskResponse second = taskService.createTask(
                new CreateTaskRequest(warehouse.getId(), goods.getId(), 3, "REQ-F2"));
        assertEquals(TaskStatus.PENDING, first.status());
        assertEquals(TaskStatus.PENDING, second.status());

        var result = replenishmentService.replenish(new ReplenishmentRequest(
                "REP-1", warehouse.getId(), "test",
                List.of(new ReplenishmentItemRequest(goods.getId(), 6))));

        assertEquals(1, result.dispatchedTasks().size());
        assertEquals(first.id(), result.dispatchedTasks().get(0).id());
        assertEquals(TaskStatus.DISPATCHED, findTask(first.id()).getStatus());
        assertEquals(TaskStatus.PENDING, findTask(second.id()).getStatus());
        assertEquals(1, currentStock());
        assertEquals(1, notificationLogRepository.countByTaskId(first.id()));
    }

    @Test
    void replenishmentDispatchesAllPendingTasksWhenEnough() {
        stock(0, LocalDateTime.now());
        PickTaskResponse first = taskService.createTask(
                new CreateTaskRequest(warehouse.getId(), goods.getId(), 2, "REQ-A1"));
        PickTaskResponse second = taskService.createTask(
                new CreateTaskRequest(warehouse.getId(), goods.getId(), 3, "REQ-A2"));

        var result = replenishmentService.replenish(new ReplenishmentRequest(
                "REP-2", warehouse.getId(), "test",
                List.of(new ReplenishmentItemRequest(goods.getId(), 5))));

        assertEquals(2, result.dispatchedTasks().size());
        assertEquals(TaskStatus.DISPATCHED, findTask(first.id()).getStatus());
        assertEquals(TaskStatus.DISPATCHED, findTask(second.id()).getStatus());
        assertEquals(0, currentStock());
    }

    @Test
    void stalePendingTaskDispatchesAfterReplenishment() {
        stock(10, LocalDateTime.now().minusMinutes(30));
        PickTaskResponse pending = taskService.createTask(
                new CreateTaskRequest(warehouse.getId(), goods.getId(), 3, "REQ-S1"));
        assertEquals("SNAPSHOT_STALE", pending.reason());

        var result = replenishmentService.replenish(new ReplenishmentRequest(
                "REP-3", warehouse.getId(), "test",
                List.of(new ReplenishmentItemRequest(goods.getId(), 5))));

        assertEquals(1, result.dispatchedTasks().size());
        assertEquals(TaskStatus.DISPATCHED, findTask(pending.id()).getStatus());
        assertEquals(12, currentStock()); // 10 + 5 - 3
    }

    @Test
    void duplicateTaskRequestIsIdempotent() {
        stock(10, LocalDateTime.now());
        PickTaskResponse first = taskService.createTask(
                new CreateTaskRequest(warehouse.getId(), goods.getId(), 3, "REQ-DUP"));
        PickTaskResponse second = taskService.createTask(
                new CreateTaskRequest(warehouse.getId(), goods.getId(), 3, "REQ-DUP"));

        assertEquals(first.id(), second.id());
        assertEquals(7, currentStock());
        assertEquals(1, notificationLogRepository.countByTaskId(first.id()));
    }

    @Test
    void duplicateReplenishmentRequestIsRejected() {
        stock(0, LocalDateTime.now());
        replenishmentService.replenish(new ReplenishmentRequest(
                "REP-DUP", warehouse.getId(), "test",
                List.of(new ReplenishmentItemRequest(goods.getId(), 5))));

        assertThrows(BusinessException.class, () -> replenishmentService.replenish(
                new ReplenishmentRequest("REP-DUP", warehouse.getId(), "test",
                        List.of(new ReplenishmentItemRequest(goods.getId(), 5)))));
        assertEquals(5, currentStock());
    }

    @Test
    void resultCallbackMovesDispatchedTaskToFinalState() {
        stock(10, LocalDateTime.now());
        PickTaskResponse dispatched = taskService.createTask(
                new CreateTaskRequest(warehouse.getId(), goods.getId(), 3, "REQ-R1"));

        PickTaskResponse completed = taskService.completeTask(dispatched.id(), TaskResult.FULFILLED);

        assertEquals(TaskStatus.FULFILLED, completed.status());
        assertEquals(TaskResult.FULFILLED, completed.result());
        assertNotNull(completed.completedAt());
        assertThrows(BusinessException.class,
                () -> taskService.completeTask(dispatched.id(), TaskResult.STOCKOUT));
    }

    private void stock(int qty, LocalDateTime syncedAt) {
        Inventory inventory = new Inventory();
        inventory.setWarehouseId(warehouse.getId());
        inventory.setGoodsId(goods.getId());
        inventory.setAvailableQty(qty);
        inventory.setSyncedAt(syncedAt);
        inventoryRepository.save(inventory);
    }

    private int currentStock() {
        return inventoryRepository.findByWarehouseIdAndGoodsId(warehouse.getId(), goods.getId())
                .map(Inventory::getAvailableQty)
                .orElse(0);
    }

    private PickTask findTask(Long id) {
        return pickTaskRepository.findById(id).orElseThrow();
    }
}
