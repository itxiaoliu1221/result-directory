package com.example.pickdispatch.order;

public enum OrderStatus {
    /** 已下单，等待库存/补货后分配 */
    PENDING,
    /** 已分配并下发拣货任务 */
    DISPATCHED,
    /** 拣货员已认领 */
    PICKING,
    /** 已拣出 */
    PICKED,
    /** 缺货 */
    STOCKOUT,
    /** 已取消 */
    CANCELLED
}
