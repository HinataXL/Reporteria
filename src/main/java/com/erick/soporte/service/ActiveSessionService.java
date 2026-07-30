package com.erick.soporte.service;

import com.erick.soporte.security.CustomUserPrincipal;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ActiveSessionService implements HttpSessionListener {

    private static final ZoneId APP_ZONE = ZoneId.of("America/Guatemala");

    private final Map<String, ActiveSession> activeSessions = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;

    public ActiveSessionService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void register(String sessionId, CustomUserPrincipal principal) {
        activeSessions.put(sessionId, new ActiveSession(
                principal.getId(),
                principal.getNombreCompleto(),
                principal.getCorreo(),
                principal.getRol(),
                LocalDateTime.now(APP_ZONE)
        ));
        publishPresenceChanged();
    }

    public List<Map<String, Object>> activeAgents() {
        LocalDateTime now = LocalDateTime.now(APP_ZONE);

        Map<String, ActiveSession> uniqueAgents = activeSessions.values()
                .stream()
                .filter(session -> "AGENTE".equalsIgnoreCase(session.role()))
                .collect(java.util.stream.Collectors.toMap(
                        this::agentKey,
                        session -> session,
                        this::oldestSession,
                        LinkedHashMap::new
                ));

        return uniqueAgents.values()
                .stream()
                .sorted(Comparator.comparing(ActiveSession::connectedAt))
                .map(session -> {
                    Duration connected = Duration.between(session.connectedAt(), now);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("userId", session.userId());
                    item.put("name", session.name());
                    item.put("email", session.email());
                    item.put("connectedAt", session.connectedAt().toString());
                    item.put("connectedFor", formatDuration(connected));
                    return item;
                })
                .toList();
    }

    private String agentKey(ActiveSession session) {
        if (session.userId() != null) {
            return "id:" + session.userId();
        }

        if (session.email() != null && !session.email().isBlank()) {
            return "email:" + session.email().trim().toLowerCase();
        }

        return "name:" + session.name().trim().toLowerCase();
    }

    private ActiveSession oldestSession(ActiveSession current, ActiveSession candidate) {
        return candidate.connectedAt().isBefore(current.connectedAt()) ? candidate : current;
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        activeSessions.remove(event.getSession().getId());
        publishPresenceChanged();
    }

    private void publishPresenceChanged() {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "presence_changed");
        event.put("timestamp", LocalDateTime.now(APP_ZONE).toString());
        messagingTemplate.convertAndSend("/topic/dashboard-events", (Object) event);
    }

    private String formatDuration(Duration duration) {
        long totalMinutes = Math.max(0, duration.toMinutes());
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;

        if (hours == 0) {
            return minutes + " min";
        }

        return minutes > 0 ? hours + " h " + minutes + " min" : hours + " h";
    }

    private record ActiveSession(
            Long userId,
            String name,
            String email,
            String role,
            LocalDateTime connectedAt
    ) {
    }
}
