package com.example.pickdispatch.notification;

import com.example.pickdispatch.task.TaskDispatchedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskDispatchedEventListener {

    private final TaskNotificationService taskNotificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(TaskDispatchedEvent event) {
        try {
            taskNotificationService.onTaskDispatched(event);
        } catch (Exception ex) {
            // Notification must never roll back a successful dispatch.
            log.error("Failed to send dispatch notification for task {}", event.taskNo(), ex);
        }
    }
}
