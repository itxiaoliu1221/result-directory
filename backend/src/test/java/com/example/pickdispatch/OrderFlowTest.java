package com.example.pickdispatch;

import com.example.pickdispatch.common.BusinessException;
import com.example.pickdispatch.goods.Goods;
import com.example.pickdispatch.goods.GoodsRepository;
import com.example.pickdispatch.inventory.Inventory;
import com.example.pickdispatch.inventory.InventoryRepository;
import com.example.pickdispatch.inventory.InventorySnapshotRepository;
import com.example.pickdispatch.notification.NotificationLogRepository;
import com.example.pickdispatch.order.CustomerOrderRepository;
import com.example.pickdispatch.order.OrderResponse;
import com.example.pickdispatch.order.OrderService;
import com.example.pickdispatch.order.OrderStatus;
import com.example.pickdispatch.order.PlaceOrderRequest;
import com.example.pickdispatch.replenishment.ReplenishmentItemRequest;
import com.example.pickdispatch.replenishment.ReplenishmentRepository;
import com.example.pickdispatch.replenishment.ReplenishmentRequest;
import com.example.pickdispatch.replenishment.ReplenishmentService;
import com.example.pickdispatch.task.PickTask;
import com.example.pickdispatch.task.PickTaskRepository;
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
class OrderFlowTest {

    @Autowired
    private OrderService orderService;
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
    private CustomerOrderRepository orderRepository;
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
        orderRepository.deleteAll();
        goodsRepository.deleteAll();
        warehouseRepository.deleteAll();

        warehouse = createWarehouse("W1", "测试仓");
        goods = createGoods("G1", "测试货物");
    }

    @Test
    void orderDispatchesImmediatelyWhenStockIsAvailable() {
        stock(warehouse.getId(), 10, LocalDateTime.now());

        OrderResponse order = orderService.placeOrder(
                new PlaceOrderRequest(goods.getId(), 3, "Alice", null));

        assertEquals(OrderStatus.DISPATCHED, order.status());
        assertNotNull(order.taskId());
        assertEquals(TaskStatus.DISPATCHED, findTask(order.taskId()).getStatus());
        assertEquals(7, stockOf(warehouse.getId()));
        assertEquals(1, notificationLogRepository.countByTaskId(order.taskId()));
    }

    @Test
    void orderStaysPendingAndDispatchesAfterReplenishment() {
        stock(warehouse.getId(), 0, LocalDateTime.now());

        OrderResponse order = orderService.placeOrder(
                new PlaceOrderRequest(goods.getId(), 4, "Bob", null));
        assertEquals(OrderStatus.PENDING, order.status());
        assertEquals(TaskStatus.PENDING, findTask(order.taskId()).getStatus());

        replenishmentService.replenish(new ReplenishmentRequest(
                "REP-ORDER-1", warehouse.getId(), "test",
                List.of(new ReplenishmentItemRequest(goods.getId(), 6))));

        OrderResponse updated = orderService.getOrder(order.id());
        assertEquals(OrderStatus.DISPATCHED, updated.status());
        assertEquals(TaskStatus.DISPATCHED, findTask(order.taskId()).getStatus());
        assertEquals(2, stockOf(warehouse.getId()));
    }

    @Test
    void claimMovesOrderToPicking() {
        stock(warehouse.getId(), 10, LocalDateTime.now());
        OrderResponse order = orderService.placeOrder(
                new PlaceOrderRequest(goods.getId(), 2, "Carol", null));

        orderService.claimTask(order.taskId(), "PDA-01");

        OrderResponse claimed = orderService.getOrder(order.id());
        assertEquals(OrderStatus.PICKING, claimed.status());
        assertEquals("PDA-01", claimed.claimedBy());
        assertThrows(BusinessException.class,
                () -> orderService.claimTask(order.taskId(), "PDA-02"));
    }

    @Test
    void completionMovesOrderToPicked() {
        stock(warehouse.getId(), 10, LocalDateTime.now());
        OrderResponse order = orderService.placeOrder(
                new PlaceOrderRequest(goods.getId(), 2, "Dave", null));
        orderService.claimTask(order.taskId(), "PDA-01");

        taskService.completeTask(order.taskId(), TaskResult.FULFILLED);

        OrderResponse completed = orderService.getOrder(order.id());
        assertEquals(OrderStatus.PICKED, completed.status());
        assertEquals(TaskStatus.FULFILLED, findTask(order.taskId()).getStatus());
    }

    @Test
    void stockoutMovesOrderToStockout() {
        stock(warehouse.getId(), 10, LocalDateTime.now());
        OrderResponse order = orderService.placeOrder(
                new PlaceOrderRequest(goods.getId(), 2, "Eve", null));
        orderService.claimTask(order.taskId(), "PDA-01");

        taskService.completeTask(order.taskId(), TaskResult.STOCKOUT);

        OrderResponse completed = orderService.getOrder(order.id());
        assertEquals(OrderStatus.STOCKOUT, completed.status());
    }

    @Test
    void orderChoosesWarehouseWithFreshStock() {
        Warehouse second = createWarehouse("W2", "第二仓");
        stock(warehouse.getId(), 1, LocalDateTime.now());
        stock(second.getId(), 8, LocalDateTime.now());

        OrderResponse order = orderService.placeOrder(
                new PlaceOrderRequest(goods.getId(), 3, "Frank", null));

        assertEquals(second.getId(), order.warehouseId());
        assertEquals(OrderStatus.DISPATCHED, order.status());
    }

    private Warehouse createWarehouse(String code, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(name);
        return warehouseRepository.save(warehouse);
    }

    private Goods createGoods(String code, String name) {
        Goods goods = new Goods();
        goods.setCode(code);
        goods.setName(name);
        goods.setCategory("测试品类");
        return goodsRepository.save(goods);
    }

    private void stock(Long warehouseId, int qty, LocalDateTime syncedAt) {
        Inventory inventory = new Inventory();
        inventory.setWarehouseId(warehouseId);
        inventory.setGoodsId(goods.getId());
        inventory.setAvailableQty(qty);
        inventory.setSyncedAt(syncedAt);
        inventoryRepository.save(inventory);
    }

    private int stockOf(Long warehouseId) {
        return inventoryRepository.findByWarehouseIdAndGoodsId(warehouseId, goods.getId())
                .map(Inventory::getAvailableQty)
                .orElse(0);
    }

    private PickTask findTask(Long id) {
        return pickTaskRepository.findById(id).orElseThrow();
    }
}
