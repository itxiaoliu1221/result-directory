package com.example.pickdispatch.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByWarehouseIdAndGoodsId(Long warehouseId, Long goodsId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.warehouseId = :warehouseId and i.goodsId = :goodsId")
    Optional<Inventory> findForUpdate(@Param("warehouseId") Long warehouseId,
                                      @Param("goodsId") Long goodsId);

    List<Inventory> findAllByOrderByWarehouseIdAscGoodsIdAsc();

    List<Inventory> findByWarehouseIdOrderByGoodsIdAsc(Long warehouseId);

    List<Inventory> findByGoodsIdOrderByAvailableQtyDesc(Long goodsId);
}
