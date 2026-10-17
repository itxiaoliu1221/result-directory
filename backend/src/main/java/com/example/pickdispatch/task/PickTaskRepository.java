package com.example.pickdispatch.task;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PickTaskRepository extends JpaRepository<PickTask, Long>, JpaSpecificationExecutor<PickTask> {

    Optional<PickTask> findByRequestNo(String requestNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PickTask t where t.id = :id")
    Optional<PickTask> findByIdForUpdate(@Param("id") Long id);

    Optional<PickTask> findByOrderId(Long orderId);

    List<PickTask> findByStatusAndWarehouseIdAndGoodsIdOrderByCreatedAtAscIdAsc(
            TaskStatus status, Long warehouseId, Long goodsId);

    List<PickTask> findTop10ByStatusOrderByCreatedAtAsc(TaskStatus status);

    long countByStatus(TaskStatus status);
}
