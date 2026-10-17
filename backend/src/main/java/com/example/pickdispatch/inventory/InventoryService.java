package com.example.pickdispatch.inventory;

import com.example.pickdispatch.config.DispatchProperties;
import com.example.pickdispatch.goods.Goods;
import com.example.pickdispatch.meta.MetaService;
import com.example.pickdispatch.warehouse.Warehouse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final MetaService metaService;
    private final DispatchProperties dispatchProperties;

    @Transactional(readOnly = true)
    public List<InventoryResponse> list(Long warehouseId) {
        List<Inventory> rows = warehouseId == null
                ? inventoryRepository.findAllByOrderByWarehouseIdAscGoodsIdAsc()
                : inventoryRepository.findByWarehouseIdOrderByGoodsIdAsc(warehouseId);
        Map<Long, Warehouse> warehouseMap = metaService.warehouseMap();
        Map<Long, Goods> goodsMap = metaService.goodsMap();
        LocalDateTime now = LocalDateTime.now();
        return rows.stream().map(row -> toResponse(row, warehouseMap, goodsMap, now)).toList();
    }

    @Transactional(readOnly = true)
    public long countStale() {
        LocalDateTime now = LocalDateTime.now();
        return inventoryRepository.findAll().stream()
                .filter(row -> isStale(row.getSyncedAt(), now))
                .count();
    }

    public boolean isStale(LocalDateTime syncedAt, LocalDateTime now) {
        int maxAge = dispatchProperties.getMaxSnapshotAgeMinutes();
        if (maxAge <= 0) {
            return false;
        }
        return Duration.between(syncedAt, now).toSeconds() >= maxAge * 60L;
    }

    public InventoryResponse toResponse(Inventory inventory,
                                        Map<Long, Warehouse> warehouseMap,
                                        Map<Long, Goods> goodsMap,
                                        LocalDateTime now) {
        Warehouse warehouse = warehouseMap.get(inventory.getWarehouseId());
        Goods goods = goodsMap.get(inventory.getGoodsId());
        long freshness = Math.max(0, Duration.between(inventory.getSyncedAt(), now).toMinutes());
        return new InventoryResponse(
                inventory.getId(),
                inventory.getWarehouseId(),
                warehouse == null ? "未知仓库" : warehouse.getName(),
                inventory.getGoodsId(),
                goods == null ? "-" : goods.getCode(),
                goods == null ? "未知货物" : goods.getName(),
                goods == null ? "-" : goods.getCategory(),
                inventory.getAvailableQty(),
                inventory.getSyncedAt(),
                freshness,
                isStale(inventory.getSyncedAt(), now)
        );
    }
}
