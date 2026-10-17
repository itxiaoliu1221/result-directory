package com.example.pickdispatch.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface PickTaskRepository extends JpaRepository<PickTask, Long>, JpaSpecificationExecutor<PickTask> {

    Optional<PickTask> findByRequestNo(String requestNo);

    List<PickTask> findByStatusAndWarehouseIdAndGoodsIdOrderByCreatedAtAscIdAsc(
            TaskStatus status, Long warehouseId, Long goodsId);

    List<PickTask> findTop10ByStatusOrderByCreatedAtAsc(TaskStatus status);

    long countByStatus(TaskStatus status);
}
