package com.example.pickdispatch.task;

import com.example.pickdispatch.common.BusinessException;
import com.example.pickdispatch.common.PageResponse;
import com.example.pickdispatch.goods.Goods;
import com.example.pickdispatch.inventory.Inventory;
import com.example.pickdispatch.inventory.InventoryRepository;
import com.example.pickdispatch.inventory.InventoryService;
import com.example.pickdispatch.meta.MetaService;
import com.example.pickdispatch.warehouse.Warehouse;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {

    private static final DateTimeFormatter TASK_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PickTaskRepository pickTaskRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;
    private final MetaService metaService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PickTaskResponse createTask(CreateTaskRequest request) {
        return createTaskInternal(request, null, null);
    }

    @Transactional
    public PickTaskResponse createTask(CreateTaskRequest request, Long orderId, String orderNo) {
        return createTaskInternal(request, orderId, orderNo);
    }

    private PickTaskResponse createTaskInternal(CreateTaskRequest request, Long orderId, String orderNo) {
        Warehouse warehouse = metaService.requireWarehouse(request.warehouseId());
        Goods goods = metaService.requireGoods(request.goodsId());

        String requestNo = normalize(request.requestNo());
        if (requestNo != null) {
            PickTask existing = pickTaskRepository.findByRequestNo(requestNo).orElse(null);
            if (existing != null) {
                if (!existing.getWarehouseId().equals(request.warehouseId())
                        || !existing.getGoodsId().equals(request.goodsId())
                        || !existing.getRequiredQty().equals(request.requiredQty())) {
                    throw BusinessException.conflict("REQUEST_NO_CONFLICT", "请求号已被其他任务使用: " + requestNo);
                }
                return toResponse(existing, warehouse, goods);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Inventory inventory = inventoryRepository
                .findForUpdate(request.warehouseId(), request.goodsId())
                .orElse(null);

        PickTask task = new PickTask();
        task.setTaskNo(generateTaskNo(now));
        task.setWarehouseId(request.warehouseId());
        task.setGoodsId(request.goodsId());
        task.setRequiredQty(request.requiredQty());
        task.setRequestNo(requestNo);
        task.setOrderId(orderId);
        task.setOrderNo(orderNo);

        if (inventory == null) {
            task.setStatus(TaskStatus.PENDING);
            task.setReason("NO_INVENTORY");
            return toResponse(pickTaskRepository.save(task), warehouse, goods);
        }

        task.setSnapshotQty(inventory.getAvailableQty());
        task.setSnapshotSyncedAt(inventory.getSyncedAt());

        if (inventoryService.isStale(inventory.getSyncedAt(), now)) {
            task.setStatus(TaskStatus.PENDING);
            task.setReason("SNAPSHOT_STALE");
            return toResponse(pickTaskRepository.save(task), warehouse, goods);
        }

        if (inventory.getAvailableQty() < request.requiredQty()) {
            task.setStatus(TaskStatus.PENDING);
            task.setReason("INSUFFICIENT_STOCK");
            return toResponse(pickTaskRepository.save(task), warehouse, goods);
        }

        int snapshotQty = inventory.getAvailableQty();
        inventory.setAvailableQty(snapshotQty - request.requiredQty());
        inventoryRepository.save(inventory);

        task.setStatus(TaskStatus.DISPATCHED);
        task.setDispatchedAt(now);
        task.setSnapshotQty(snapshotQty);
        task.setSnapshotSyncedAt(inventory.getSyncedAt());
        PickTask saved = pickTaskRepository.save(task);

        eventPublisher.publishEvent(new TaskDispatchedEvent(
                saved.getId(),
                saved.getTaskNo(),
                saved.getOrderId(),
                saved.getOrderNo(),
                saved.getWarehouseId(),
                warehouse.getName(),
                saved.getGoodsId(),
                goods.getName(),
                saved.getRequiredQty(),
                now
        ));
        return toResponse(saved, warehouse, goods);
    }

    @Transactional(readOnly = true)
    public PageResponse<PickTaskResponse> list(TaskStatus status,
                                               Long warehouseId,
                                               Long goodsId,
                                               int page,
                                               int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<PickTask> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (warehouseId != null) {
                predicates.add(cb.equal(root.get("warehouseId"), warehouseId));
            }
            if (goodsId != null) {
                predicates.add(cb.equal(root.get("goodsId"), goodsId));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<PickTask> result = pickTaskRepository.findAll(specification, pageable);
        Map<Long, Warehouse> warehouseMap = metaService.warehouseMap();
        Map<Long, Goods> goodsMap = metaService.goodsMap();
        List<PickTaskResponse> content = result.getContent().stream()
                .map(task -> toResponse(task, warehouseMap.get(task.getWarehouseId()), goodsMap.get(task.getGoodsId())))
                .toList();
        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PickTaskResponse getTask(Long id) {
        PickTask task = pickTaskRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("任务不存在: " + id));
        return toResponse(task,
                metaService.requireWarehouse(task.getWarehouseId()),
                metaService.requireGoods(task.getGoodsId()));
    }

    @Transactional
    public PickTaskResponse completeTask(Long id, TaskResult result) {
        PickTask task = pickTaskRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("任务不存在: " + id));
        if (task.getStatus() != TaskStatus.DISPATCHED) {
            throw BusinessException.conflict("INVALID_TASK_STATE", "只有已下发任务可以回传结果");
        }
        task.setStatus(result == TaskResult.FULFILLED ? TaskStatus.FULFILLED : TaskStatus.STOCKOUT);
        task.setResult(result);
        LocalDateTime completedAt = LocalDateTime.now();
        task.setCompletedAt(completedAt);
        PickTask saved = pickTaskRepository.save(task);
        eventPublisher.publishEvent(new TaskCompletedEvent(
                saved.getId(),
                saved.getTaskNo(),
                saved.getOrderId(),
                saved.getOrderNo(),
                result,
                completedAt
        ));
        return toResponse(saved,
                metaService.requireWarehouse(saved.getWarehouseId()),
                metaService.requireGoods(saved.getGoodsId()));
    }

    @Transactional(readOnly = true)
    public List<PickTaskResponse> recentPending() {
        Map<Long, Warehouse> warehouseMap = metaService.warehouseMap();
        Map<Long, Goods> goodsMap = metaService.goodsMap();
        return pickTaskRepository.findTop10ByStatusOrderByCreatedAtAsc(TaskStatus.PENDING).stream()
                .map(task -> toResponse(task, warehouseMap.get(task.getWarehouseId()), goodsMap.get(task.getGoodsId())))
                .toList();
    }

    public PickTaskResponse toResponse(PickTask task, Warehouse warehouse, Goods goods) {
        Long snapshotAgeMinutes = null;
        if (task.getSnapshotSyncedAt() != null && task.getCreatedAt() != null) {
            LocalDateTime base = task.getDispatchedAt() != null ? task.getDispatchedAt() : task.getCreatedAt();
            snapshotAgeMinutes = Math.max(0, Duration.between(task.getSnapshotSyncedAt(), base).toMinutes());
        }
        return new PickTaskResponse(
                task.getId(),
                task.getTaskNo(),
                task.getOrderId(),
                task.getOrderNo(),
                task.getWarehouseId(),
                warehouse == null ? "未知仓库" : warehouse.getName(),
                task.getGoodsId(),
                goods == null ? "未知货物" : goods.getName(),
                goods == null ? "-" : goods.getCategory(),
                task.getRequiredQty(),
                task.getStatus(),
                task.getReason(),
                task.getSnapshotQty(),
                task.getSnapshotSyncedAt(),
                snapshotAgeMinutes,
                task.getClaimedBy(),
                task.getClaimedAt(),
                task.getCreatedAt(),
                task.getDispatchedAt(),
                task.getCompletedAt(),
                task.getResult(),
                task.getRequestNo()
        );
    }

    private String generateTaskNo(LocalDateTime now) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "PT-" + TASK_NO_TIME.format(now) + "-" + suffix;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
