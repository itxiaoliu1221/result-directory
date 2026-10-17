package com.example.pickdispatch.realtime;

import com.example.pickdispatch.order.CustomerOrder;
import com.example.pickdispatch.order.CustomerOrderRepository;
import com.example.pickdispatch.order.OrderStatus;
import com.example.pickdispatch.task.TaskResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderRealtimeService {

    private final CustomerOrderRepository orderRepository;

    /**
     * Called after a replenishment-triggered dispatch. Runs in a new transaction
     * because the original dispatch transaction has already committed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDispatched(Long orderId, Long taskId) {
        orderRepository.findById(orderId).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.DISPATCHED);
                order.setDispatchedAt(LocalDateTime.now());
                order.setTaskId(taskId);
                orderRepository.save(order);
                log.info("Order {} moved to DISPATCHED after replenishment", order.getOrderNo());
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(Long orderId, TaskResult result) {
        orderRepository.findById(orderId).ifPresent(order -> {
            if (order.getStatus() != OrderStatus.PICKED
                    && order.getStatus() != OrderStatus.STOCKOUT
                    && order.getStatus() != OrderStatus.CANCELLED) {
                order.setStatus(result == TaskResult.FULFILLED
                        ? OrderStatus.PICKED
                        : OrderStatus.STOCKOUT);
                order.setCompletedAt(LocalDateTime.now());
                orderRepository.save(order);
                log.info("Order {} completed with result {}", order.getOrderNo(), result);
            }
        });
    }
}
