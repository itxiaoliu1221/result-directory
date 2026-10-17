package com.example.pickdispatch.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "inventory",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inventory_warehouse_goods",
                columnNames = {"warehouse_id", "goods_id"}
        )
)
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "goods_id", nullable = false)
    private Long goodsId;

    @Column(name = "available_qty", nullable = false)
    private Integer availableQty;

    /** Last time the stock snapshot was refreshed by a sync or replenishment. */
    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    @Version
    private Long version;
}
