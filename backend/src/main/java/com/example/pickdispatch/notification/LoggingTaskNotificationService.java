package com.example.pickdispatch.notification;

import com.example.pickdispatch.task.TaskDispatchedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoggingTaskNotificationService implements TaskNotificationService {

    private final NotificationLogRepository notificationLogRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTaskDispatched(TaskDispatchedEvent event) {
        String payload = "taskNo=" + event.taskNo()
                + ", warehouse=" + event.warehouseName()
                + ", goods=" + event.goodsName()
                + ", qty=" + event.requiredQty();
        log.info("[通知桩] 拣货任务已下发: {}", payload);

        NotificationLog row = new NotificationLog();
        row.setTaskId(event.taskId());
        row.setEventType("TASK_DISPATCHED");
        row.setChannel("LOG");
        row.setPayload(payload);
        row.setStatus("SUCCESS");
        notificationLogRepository.save(row);
    }
}
