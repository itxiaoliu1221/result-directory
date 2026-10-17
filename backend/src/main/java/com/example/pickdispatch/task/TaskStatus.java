package com.example.pickdispatch.task;

public enum TaskStatus {
    /** 待下发：库存不足或快照过期，等待补货/同步后重扫 */
    PENDING,
    /** 已下发：库存已预占，等待拣货结果 */
    DISPATCHED,
    /** 已拣出 */
    FULFILLED,
    /** 到位缺货 */
    STOCKOUT,
    /** 已取消 */
    CANCELLED
}
