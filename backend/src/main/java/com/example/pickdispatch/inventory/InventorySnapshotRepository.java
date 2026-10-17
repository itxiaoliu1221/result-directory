package com.example.pickdispatch.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventorySnapshotRepository extends JpaRepository<InventorySnapshot, Long> {

    List<InventorySnapshot> findTop20ByWarehouseIdAndGoodsIdOrderByCreatedAtDesc(Long warehouseId, Long goodsId);
}
