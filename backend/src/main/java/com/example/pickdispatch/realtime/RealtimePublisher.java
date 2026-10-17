package com.example.pickdispatch.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RealtimePublisher {

    private final PickingWebSocketHandler webSocketHandler;

    public void publish(String type, Map<String, Object> data) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("eventId", UUID.randomUUID().toString());
        message.put("type", type);
        message.put("timestamp", LocalDateTime.now().toString());
        message.put("data", data);
        webSocketHandler.broadcast(message);
    }
}
