package com.example.pickdispatch.replenishment;

import com.example.pickdispatch.common.BusinessException;
import com.example.pickdispatch.goods.Goods;
import com.example.pickdispatch.inventory.Inventory;
import com.example.pickdispatch.inventory.InventoryRepository;
import com.example.pickdispatch.inventory.InventorySnapshot;
import com.example.pickdispatch.inventory.InventorySnapshotRepository;
import com.example.pickdispatch.meta.MetaService;
import com.example.pickdispatch.task.PickTask;
import com.example.pickdispatch.task.PickTaskRepository;
import com.example.pickdispatch.task.PickTaskResponse;
import com.example.pickdispatch.task.TaskDispatchedEvent;
import com.example.pickdispatch.task.TaskService;
import com.example.pickdispatch.task.TaskStatus;
import com.example.pickdispatch.warehouse.Warehouse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class ReplenishmentService {

    private final ReplenishmentRepository replenishmentRepository;
    private final InventoryRepository inventoryRepository;
    private final InventorySnapshotRepository snapshotRepository;
    private final PickTaskRepository pickTaskRepository;
    private final MetaService metaService;
    private final TaskService taskService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ReplenishmentResponse replenish(ReplenishmentRequest request) {
        Warehouse warehouse = metaService.requireWarehouse(request.warehouseId());
        String requestNo = normalize(request.requestNo());
        if (requestNo != null && replenishmentRepository.findByRequestNo(requestNo).isPresent()) {
            throw BusinessException.conflict("DUPLICATE_REQUEST", "补货单号已存在: " + requestNo);
        }

        // Validate and merge items. A goods line may appear only once in one request.
        Map<Long, Integer> quantityByGoods = new TreeMap<>();
        Map<Long, Goods> goodsById = new HashMap<>();
        for (ReplenishmentItemRequest item : request.items()) {
            Goods goods = metaService.requireGoods(item.goodsId());
            if (quantityByGoods.containsKey(item.goodsId())) {
                throw BusinessException.badRequest("同一补货单中货物不能重复: " + goods.getName());
            }
            quantityByGoods.put(item.goodsId(), item.quantity());
            goodsById.put(item.goodsId(), goods);
        }

        Replenishment replenishment = new Replenishment();
        replenishment.setRequestNo(requestNo);
        replenishment.setWarehouseId(request.warehouseId());
        replenishment.setSource(StringUtils.hasText(request.source()) ? request.source().trim() : "manual");
        for (Map.Entry<Long, Integer> entry : quantityByGoods.entrySet()) {
            ReplenishmentItem item = new ReplenishmentItem();
            item.setGoodsId(entry.getKey());
            item.setQuantity(entry.getValue());
            replenishment.addItem(item);
        }
        Replenishment savedReplenishment = replenishmentRepository.save(replenishment);

        LocalDateTime now = LocalDateTime.now();
        List<PickTask> dispatched = new ArrayList<>();
        List<ReplenishmentResponse.ItemResult> itemResults = new ArrayList<>();

        // Lock inventory rows in goods-id order to keep lock ordering consistent.
        for (Map.Entry<Long, Integer> entry : quantityByGoods.entrySet()) {
            Long goodsId = entry.getKey();
            int replenishedQty = entry.getValue();
            Goods goods = goodsById.get(goodsId);

            Inventory inventory = inventoryRepository.findForUpdate(request.warehouseId(), goodsId)
                    .orElseGet(() -> newInventory(request.warehouseId(), goodsId, now));

            int afterReplenish = inventory.getAvailableQty() + replenishedQty;
            inventory.setAvailableQty(afterReplenish);
            inventory.setSyncedAt(now);
            inventoryRepository.save(inventory);

            InventorySnapshot snapshot = new InventorySnapshot();
            snapshot.setWarehouseId(request.warehouseId());
            snapshot.setGoodsId(goodsId);
            snapshot.setQuantity(afterReplenish);
            snapshot.setSource("REPLENISHMENT");
            snapshot.setSyncedAt(now);
            InventorySnapshot savedSnapshot = snapshotRepository.save(snapshot);

            List<PickTask> pendingTasks = pickTaskRepository
                    .findByStatusAndWarehouseIdAndGoodsIdOrderByCreatedAtAscIdAsc(
                            TaskStatus.PENDING, request.warehouseId(), goodsId);

            int dispatchedCount = 0;
            for (PickTask task : pendingTasks) {
                if (inventory.getAvailableQty() < task.getRequiredQty()) {
                    break; // strict FIFO: do not skip ahead to a smaller task
                }
                int snapshotQty = inventory.getAvailableQty();
                inventory.setAvailableQty(snapshotQty - task.getRequiredQty());

                task.setStatus(TaskStatus.DISPATCHED);
                task.setDispatchedAt(now);
                task.setReason(null);
                task.setSnapshotId(savedSnapshot.getId());
                task.setSnapshotQty(snapshotQty);
                task.setSnapshotSyncedAt(now);
                pickTaskRepository.save(task);
                dispatched.add(task);
                dispatchedCount++;

                eventPublisher.publishEvent(new TaskDispatchedEvent(
                        task.getId(),
                        task.getTaskNo(),
                        task.getOrderId(),
                        task.getOrderNo(),
                        task.getWarehouseId(),
                        warehouse.getName(),
                        task.getGoodsId(),
                        goods.getName(),
                        task.getRequiredQty(),
                        now
                ));
            }

            inventoryRepository.save(inventory);
            itemResults.add(new ReplenishmentResponse.ItemResult(
                    goodsId,
                    goods.getName(),
                    replenishedQty,
                    inventory.getAvailableQty(),
                    dispatchedCount
            ));
        }

        List<PickTaskResponse> dispatchedResponses = dispatched.stream()
                .map(task -> taskService.toResponse(task, warehouse, goodsById.get(task.getGoodsId())))
                .toList();

        return new ReplenishmentResponse(
                savedReplenishment.getId(),
                savedReplenishment.getRequestNo(),
                warehouse.getName(),
                itemResults,
                dispatchedResponses
        );
    }

    @Transactional(readOnly = true)
    public List<ReplenishmentHistoryResponse> history() {
        Map<Long, Warehouse> warehouseMap = metaService.warehouseMap();
        Map<Long, Goods> goodsMap = metaService.goodsMap();
        return replenishmentRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(replenishment -> {
                    Warehouse warehouse = warehouseMap.get(replenishment.getWarehouseId());
                    List<ReplenishmentHistoryResponse.Item> items = replenishment.getItems().stream()
                            .map(item -> {
                                Goods goods = goodsMap.get(item.getGoodsId());
                                return new ReplenishmentHistoryResponse.Item(
                                        item.getGoodsId(),
                                        goods == null ? "未知货物" : goods.getName(),
                                        item.getQuantity()
                                );
                            })
                            .toList();
                    return new ReplenishmentHistoryResponse(
                            replenishment.getId(),
                            replenishment.getRequestNo(),
                            replenishment.getWarehouseId(),
                            warehouse == null ? "未知仓库" : warehouse.getName(),
                            replenishment.getSource(),
                            replenishment.getCreatedAt(),
                            items
                    );
                })
                .toList();
    }

    private Inventory newInventory(Long warehouseId, Long goodsId, LocalDateTime now) {
        Inventory inventory = new Inventory();
        inventory.setWarehouseId(warehouseId);
        inventory.setGoodsId(goodsId);
        inventory.setAvailableQty(0);
        inventory.setSyncedAt(now);
        return inventory;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
