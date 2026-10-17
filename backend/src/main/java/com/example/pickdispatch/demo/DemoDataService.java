package com.example.pickdispatch.demo;

import com.example.pickdispatch.goods.Goods;
import com.example.pickdispatch.goods.GoodsRepository;
import com.example.pickdispatch.inventory.Inventory;
import com.example.pickdispatch.inventory.InventoryRepository;
import com.example.pickdispatch.inventory.InventorySnapshot;
import com.example.pickdispatch.inventory.InventorySnapshotRepository;
import com.example.pickdispatch.task.CreateTaskRequest;
import com.example.pickdispatch.task.TaskService;
import com.example.pickdispatch.warehouse.Warehouse;
import com.example.pickdispatch.warehouse.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Demo-only master data and stock. The quantities below are illustrative and
 * must not be used as evidence for any business conclusion.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoDataService {

    private static final String[] WAREHOUSE_CODES = {"SW", "EC", "NC", "SC"};
    private static final String[] WAREHOUSE_NAMES = {
            "西南枢纽转运仓", "华东一号分拨仓", "华北综合物流园", "华南保税中心仓"
    };
    private static final String[] GOODS_NAMES = {
            "粮油副食", "宠物主粮", "母婴用品", "美妆护肤", "生鲜冷链",
            "家居清洁", "数码配件", "日用个护", "休闲零食", "酒水饮料"
    };

    private final WarehouseRepository warehouseRepository;
    private final GoodsRepository goodsRepository;
    private final InventoryRepository inventoryRepository;
    private final InventorySnapshotRepository snapshotRepository;
    private final TaskService taskService;

    @Transactional
    public void initialize() {
        if (warehouseRepository.count() > 0) {
            log.info("Demo data already present, skip seeding");
            return;
        }

        List<Warehouse> warehouses = new ArrayList<>();
        for (int i = 0; i < WAREHOUSE_NAMES.length; i++) {
            Warehouse warehouse = new Warehouse();
            warehouse.setCode(WAREHOUSE_CODES[i]);
            warehouse.setName(WAREHOUSE_NAMES[i]);
            warehouses.add(warehouseRepository.save(warehouse));
        }

        List<Goods> goodsList = new ArrayList<>();
        for (int i = 0; i < GOODS_NAMES.length; i++) {
            Goods goods = new Goods();
            goods.setCode(String.format("G%02d", i + 1));
            goods.setName(GOODS_NAMES[i]);
            goods.setCategory(GOODS_NAMES[i]);
            goodsList.add(goodsRepository.save(goods));
        }

        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < warehouses.size(); i++) {
            for (int j = 0; j < goodsList.size(); j++) {
                int qty;
                if (j == 0) {
                    qty = 0;
                } else if (j == 1) {
                    qty = i + 1;
                } else {
                    qty = 12 + (i * 3 + j * 2) % 14;
                }
                LocalDateTime syncedAt = now.minusMinutes((i + j) % 3);
                // One deliberately stale row so the freshness guard can be demonstrated.
                if (i == 3 && j == 9) {
                    syncedAt = now.minusMinutes(30);
                }

                Inventory inventory = new Inventory();
                inventory.setWarehouseId(warehouses.get(i).getId());
                inventory.setGoodsId(goodsList.get(j).getId());
                inventory.setAvailableQty(qty);
                inventory.setSyncedAt(syncedAt);
                inventoryRepository.save(inventory);

                InventorySnapshot snapshot = new InventorySnapshot();
                snapshot.setWarehouseId(warehouses.get(i).getId());
                snapshot.setGoodsId(goodsList.get(j).getId());
                snapshot.setQuantity(qty);
                snapshot.setSource("INIT");
                snapshot.setSyncedAt(syncedAt);
                snapshotRepository.save(snapshot);
            }
        }

        // Two pending tasks make the queue visible on first launch.
        taskService.createTask(new CreateTaskRequest(
                warehouses.get(0).getId(), goodsList.get(0).getId(), 5, "DEMO-PENDING-001"));
        taskService.createTask(new CreateTaskRequest(
                warehouses.get(3).getId(), goodsList.get(9).getId(), 2, "DEMO-PENDING-002"));

        log.info("Demo data seeded: {} warehouses, {} goods, {} inventory rows",
                warehouses.size(), goodsList.size(), warehouses.size() * goodsList.size());
    }
}
