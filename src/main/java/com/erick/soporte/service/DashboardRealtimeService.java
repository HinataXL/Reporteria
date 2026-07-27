package com.erick.soporte.service;

import com.erick.soporte.entity.Conversation;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DashboardRealtimeService {

    private final SimpMessagingTemplate messagingTemplate;

    public DashboardRealtimeService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishConversationChanged(String action, Conversation conversation) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "conversation_changed");
        event.put("action", action);
        event.put("conversationId", conversation.getId());
        event.put("codigo", conversation.getCodigo());
        event.put("userId", conversation.getUserId());
        event.put("agente", conversation.getAgenteNombre());
        event.put("statusId", conversation.getStatusId());
        event.put("timestamp", LocalDateTime.now().toString());

        messagingTemplate.convertAndSend("/topic/dashboard-events", (Object) event);
    }
}
