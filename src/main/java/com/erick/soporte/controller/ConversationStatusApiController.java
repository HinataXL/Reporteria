package com.erick.soporte.controller;

import com.erick.soporte.entity.Conversation;
import com.erick.soporte.repository.ConversationRepository;
import com.erick.soporte.security.CustomUserPrincipal;
import com.erick.soporte.service.DashboardRealtimeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class ConversationStatusApiController {

    private final ConversationRepository conversationRepository;
    private final DashboardRealtimeService dashboardRealtimeService;

    public ConversationStatusApiController(
            ConversationRepository conversationRepository,
            DashboardRealtimeService dashboardRealtimeService
    ) {
        this.conversationRepository = conversationRepository;
        this.dashboardRealtimeService = dashboardRealtimeService;
    }

    @PostMapping("/api/conversations/{id}/status")
    public Map<String, Object> updateStatus(
            @PathVariable Long id,
            @RequestParam Long statusId,
            Authentication authentication
    ) {
        CustomUserPrincipal user = (CustomUserPrincipal) authentication.getPrincipal();
        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversacion no encontrada"));

        if (!canUpdateConversation(user, conversation)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para cambiar esta conversacion");
        }
        if (!isValidStatus(statusId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado no valido");
        }

        conversation.setStatusId(statusId);
        if ((statusId == 3 || statusId == 5) && conversation.getFechaFinalizacion() == null) {
            conversation.setFechaFinalizacion(LocalDateTime.now(ZoneId.of("America/Guatemala")));
        }

        Conversation saved = conversationRepository.save(conversation);
        dashboardRealtimeService.publishConversationChanged("updated", saved);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", saved.getId());
        response.put("statusId", saved.getStatusId());
        response.put("statusName", statusName(saved.getStatusId()));
        return response;
    }

    private boolean canUpdateConversation(CustomUserPrincipal user, Conversation conversation) {
        if (user.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPERVISOR"))) {
            return true;
        }

        return (conversation.getUserId() != null && conversation.getUserId().equals(user.getId()))
                || (conversation.getAgenteNombre() != null
                && conversation.getAgenteNombre().equalsIgnoreCase(user.getNombreCompleto()));
    }

    private boolean isValidStatus(Long statusId) {
        return statusId != null && statusId >= 1 && statusId <= 5;
    }

    private String statusName(Long id) {
        return switch (id.intValue()) {
            case 1 -> "Pendiente";
            case 2 -> "En Proceso";
            case 3 -> "Resuelto";
            case 4 -> "Escalado";
            case 5 -> "Cerrado";
            default -> "Desconocido";
        };
    }
}
