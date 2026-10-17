package com.example.pickdispatch.realtime;

import com.example.pickdispatch.task.TaskClaimedEvent;
import com.example.pickdispatch.task.TaskCompletedEvent;
import com.example.pickdispatch.task.TaskDispatchedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskRealtimeEventListener {

    private final RealtimePublisher realtimePublisher;
    private final OrderRealtimeService orderRealtimeService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDispatched(TaskDispatchedEvent event) {
        log.info("Realtime dispatch event received: taskNo={}, orderNo={}", event.taskNo(), event.orderNo());
        if (event.orderId() != null) {
            try {
                orderRealtimeService.markDispatched(event.orderId(), event.taskId());
            } catch (Exception ex) {
                log.error("Failed to update order {} after dispatch", event.orderId(), ex);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", event.taskId());
        data.put("taskNo", event.taskNo());
        data.put("orderId", event.orderId());
        data.put("orderNo", event.orderNo());
        data.put("warehouseId", event.warehouseId());
        data.put("warehouseName", event.warehouseName());
        data.put("goodsId", event.goodsId());
        data.put("goodsName", event.goodsName());
        data.put("quantity", event.requiredQty());
        data.put("status", "DISPATCHED");
        data.put("dispatchedAt", event.dispatchedAt());
        realtimePublisher.publish("TASK_DISPATCHED", data);
        log.info("Realtime TASK_DISPATCHED published: taskNo={}", event.taskNo());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClaimed(TaskClaimedEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", event.taskId());
        data.put("taskNo", event.taskNo());
        data.put("orderId", event.orderId());
        data.put("orderNo", event.orderNo());
        data.put("pickerName", event.pickerName());
        data.put("status", "PICKING");
        data.put("claimedAt", event.claimedAt());
        realtimePublisher.publish("TASK_CLAIMED", data);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompleted(TaskCompletedEvent event) {
        if (event.orderId() != null) {
            try {
                orderRealtimeService.markCompleted(event.orderId(), event.result());
            } catch (Exception ex) {
                log.error("Failed to update order {} after completion", event.orderId(), ex);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", event.taskId());
        data.put("taskNo", event.taskNo());
        data.put("orderId", event.orderId());
        data.put("orderNo", event.orderNo());
        data.put("result", event.result().name());
        data.put("status", event.result().name());
        data.put("completedAt", event.completedAt());
        realtimePublisher.publish("TASK_COMPLETED", data);
    }
}
