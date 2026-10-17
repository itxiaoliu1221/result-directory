package com.example.pickdispatch.notification;

import com.example.pickdispatch.task.TaskDispatchedEvent;

/**
 * Notification boundary. The default implementation only writes a log row.
 * Replace it with an SMS/IM/Email implementation without touching dispatch logic.
 */
public interface TaskNotificationService {

    void onTaskDispatched(TaskDispatchedEvent event);
}
