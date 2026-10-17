package com.example.pickdispatch.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "pick_task",
        indexes = @Index(
                name = "idx_pick_task_queue",
                columnList = "status,warehouse_id,goods_id,created_at"
        )
)
public class PickTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_no", nullable = false, unique = true, length = 48)
    private String taskNo;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "goods_id", nullable = false)
    private Long goodsId;

    @Column(name = "required_qty", nullable = false)
    private Integer requiredQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    /** INSUFFICIENT_STOCK / SNAPSHOT_STALE / NO_INVENTORY */
    @Column(length = 32)
    private String reason;

    @Column(name = "snapshot_id")
    private Long snapshotId;

    @Column(name = "snapshot_qty")
    private Integer snapshotQty;

    @Column(name = "snapshot_synced_at")
    private LocalDateTime snapshotSyncedAt;

    @Column(name = "request_no", unique = true, length = 64)
    private String requestNo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TaskResult result;
}
