package com.example.pickdispatch.order;

import com.example.pickdispatch.common.BusinessException;
import com.example.pickdispatch.goods.Goods;
import com.example.pickdispatch.inventory.Inventory;
import com.example.pickdispatch.inventory.InventoryRepository;
import com.example.pickdispatch.inventory.InventoryService;
import com.example.pickdispatch.meta.MetaService;
import com.example.pickdispatch.task.CreateTaskRequest;
import com.example.pickdispatch.task.PickTask;
import com.example.pickdispatch.task.PickTaskRepository;
import com.example.pickdispatch.task.PickTaskResponse;
import com.example.pickdispatch.task.TaskClaimedEvent;
import com.example.pickdispatch.task.TaskService;
import com.example.pickdispatch.task.TaskStatus;
import com.example.pickdispatch.warehouse.Warehouse;
import com.example.pickdispatch.warehouse.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final CustomerOrderRepository orderRepository;
    private final PickTaskRepository pickTaskRepository;
    private final InventoryRepository inventoryRepository;
    private final WarehouseRepository warehouseRepository;
    private final MetaService metaService;
    private final InventoryService inventoryService;
    private final TaskService taskService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Place an order. The backend chooses a warehouse with enough fresh stock if
     * warehouseId is omitted. If no stock is available, a pending pick task is
     * created and will be dispatched after a replenishment/sync.
     */
    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest request) {
        Goods goods = metaService.requireGoods(request.goodsId());
        Long warehouseId = request.warehouseId() != null
                ? request.warehouseId()
                : chooseWarehouse(request.goodsId(), request.quantity());
        Warehouse warehouse = metaService.requireWarehouse(warehouseId);

        String orderNo = generateOrderNo();
        CustomerOrder order = new CustomerOrder();
        order.setOrderNo(orderNo);
        order.setCustomerName(StringUtils.hasText(request.customerName())
                ? request.customerName().trim()
                : "匿名用户");
        order.setWarehouseId(warehouseId);
        order.setGoodsId(request.goodsId());
        order.setQuantity(request.quantity());
        order.setStatus(OrderStatus.PENDING);
        order = orderRepository.save(order);

        PickTaskResponse task = taskService.createTask(
                new CreateTaskRequest(warehouseId, request.goodsId(), request.quantity(), orderNo),
                order.getId(),
                orderNo
        );

        order.setTaskId(task.id());
        if (task.status() == TaskStatus.DISPATCHED) {
            order.setStatus(OrderStatus.DISPATCHED);
            order.setDispatchedAt(task.dispatchedAt() != null ? task.dispatchedAt() : LocalDateTime.now());
        }
        order = orderRepository.save(order);
        return toResponse(order, pickTaskRepository.findById(task.id()).orElse(null), warehouse, goods);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(OrderStatus status) {
        List<CustomerOrder> orders = status == null
                ? orderRepository.findTop50ByOrderByCreatedAtDesc()
                : orderRepository.findTop50ByStatusOrderByCreatedAtDesc(status);
        Map<Long, Warehouse> warehouseMap = metaService.warehouseMap();
        Map<Long, Goods> goodsMap = metaService.goodsMap();
        return orders.stream()
                .map(order -> toResponse(
                        order,
                        order.getTaskId() == null ? null : pickTaskRepository.findById(order.getTaskId()).orElse(null),
                        warehouseMap.get(order.getWarehouseId()),
                        goodsMap.get(order.getGoodsId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id) {
        CustomerOrder order = orderRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("订单不存在: " + id));
        return toResponse(
                order,
                order.getTaskId() == null ? null : pickTaskRepository.findById(order.getTaskId()).orElse(null),
                metaService.requireWarehouse(order.getWarehouseId()),
                metaService.requireGoods(order.getGoodsId()));
    }

    @Transactional
    public void claimTask(Long taskId, String pickerName) {
        PickTask task = pickTaskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> BusinessException.notFound("任务不存在: " + taskId));
        if (task.getStatus() != TaskStatus.DISPATCHED) {
            throw BusinessException.conflict("INVALID_TASK_STATE", "只有已下发任务可以认领");
        }
        if (StringUtils.hasText(task.getClaimedBy())) {
            throw BusinessException.conflict("TASK_ALREADY_CLAIMED", "任务已被认领");
        }

        LocalDateTime now = LocalDateTime.now();
        task.setClaimedBy(pickerName.trim());
        task.setClaimedAt(now);
        pickTaskRepository.save(task);

        if (task.getOrderId() != null) {
            orderRepository.findById(task.getOrderId()).ifPresent(order -> {
                order.setStatus(OrderStatus.PICKING);
                order.setClaimedAt(now);
                order.setTaskId(task.getId());
                orderRepository.save(order);
            });
        }

        eventPublisher.publishEvent(new TaskClaimedEvent(
                task.getId(),
                task.getTaskNo(),
                task.getOrderId(),
                task.getOrderNo(),
                task.getClaimedBy(),
                now
        ));
    }

    private Long chooseWarehouse(Long goodsId, int quantity) {
        List<Inventory> rows = inventoryRepository.findByGoodsIdOrderByAvailableQtyDesc(goodsId);
        LocalDateTime now = LocalDateTime.now();

        Long chosen = rows.stream()
                .filter(row -> row.getAvailableQty() >= quantity
                        && !inventoryService.isStale(row.getSyncedAt(), now))
                .map(Inventory::getWarehouseId)
                .findFirst()
                .orElse(null);
        if (chosen == null) {
            chosen = rows.stream()
                    .filter(row -> row.getAvailableQty() >= quantity)
                    .map(Inventory::getWarehouseId)
                    .findFirst()
                    .orElse(null);
        }
        if (chosen == null) {
            chosen = rows.stream().map(Inventory::getWarehouseId).findFirst().orElse(null);
        }
        if (chosen == null) {
            chosen = warehouseRepository.findAllByOrderByIdAsc().stream()
                    .map(Warehouse::getId)
                    .findFirst()
                    .orElseThrow(() -> BusinessException.badRequest("没有可用仓库"));
        }
        return chosen;
    }

    private OrderResponse toResponse(CustomerOrder order,
                                     PickTask task,
                                     Warehouse warehouse,
                                     Goods goods) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getCustomerName(),
                order.getWarehouseId(),
                warehouse == null ? "未知仓库" : warehouse.getName(),
                order.getGoodsId(),
                goods == null ? "未知货物" : goods.getName(),
                order.getQuantity(),
                order.getStatus(),
                order.getTaskId(),
                task == null ? null : task.getTaskNo(),
                task == null ? null : task.getStatus(),
                task == null ? null : task.getClaimedBy(),
                order.getCreatedAt(),
                order.getDispatchedAt(),
                order.getClaimedAt(),
                order.getCompletedAt()
        );
    }

    private String generateOrderNo() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "SO-" + ORDER_NO_TIME.format(LocalDateTime.now()) + "-" + suffix;
    }
}
