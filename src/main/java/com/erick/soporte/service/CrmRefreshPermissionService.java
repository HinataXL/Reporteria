package com.erick.soporte.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class CrmRefreshPermissionService {

    private final SimpMessagingTemplate messagingTemplate;
    private final AtomicLong sequence = new AtomicLong(1);
    private final List<RefreshRequest> requests = new CopyOnWriteArrayList<>();

    public CrmRefreshPermissionService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public RefreshRequest request(
            String agentName,
            String agentEmail,
            LocalDate from,
            LocalDate to,
            String taskType,
            String operationCountry,
            String status,
            Integer size
    ) {
        RefreshRequest request = new RefreshRequest(
                sequence.getAndIncrement(),
                agentName,
                agentEmail,
                from,
                to,
                clean(taskType),
                clean(operationCountry),
                clean(status),
                size,
                RequestStatus.PENDING,
                LocalDateTime.now(),
                null
        );
        requests.add(request);
        publishRequestCreated(request);
        return request;
    }

    public List<RefreshRequest> pendingRequests() {
        return requests.stream()
                .filter(request -> request.status() == RequestStatus.PENDING)
                .sorted(Comparator.comparing(RefreshRequest::requestedAt).reversed())
                .toList();
    }

    public boolean approve(long id) {
        RefreshRequest request = updateStatus(id, RequestStatus.APPROVED);
        if (request != null) {
            publishRequestResolved(request);
            return true;
        }
        return false;
    }

    public boolean reject(long id) {
        RefreshRequest request = updateStatus(id, RequestStatus.REJECTED);
        if (request != null) {
            publishRequestResolved(request);
            return true;
        }
        return false;
    }

    public boolean hasApproved(
            String agentName,
            LocalDate from,
            LocalDate to,
            String taskType,
            String operationCountry,
            String status,
            Integer size
    ) {
        return requests.stream().anyMatch(request ->
                request.status() == RequestStatus.APPROVED
                        && matches(request, agentName, from, to, taskType, operationCountry, status, size)
        );
    }

    public boolean consumeApproved(
            String agentName,
            LocalDate from,
            LocalDate to,
            String taskType,
            String operationCountry,
            String status,
            Integer size
    ) {
        for (int i = 0; i < requests.size(); i++) {
            RefreshRequest request = requests.get(i);
            if (request.status() == RequestStatus.APPROVED
                    && matches(request, agentName, from, to, taskType, operationCountry, status, size)) {
                requests.set(i, request.withStatus(RequestStatus.USED));
                return true;
            }
        }
        return false;
    }

    private RefreshRequest updateStatus(long id, RequestStatus status) {
        for (int i = 0; i < requests.size(); i++) {
            RefreshRequest request = requests.get(i);
            if (request.id() == id) {
                RefreshRequest updated = request.withStatus(status);
                requests.set(i, updated);
                return updated;
            }
        }
        return null;
    }

    private void publishRequestCreated(RefreshRequest request) {
        Map<String, Object> event = requestEvent(request);
        event.put("type", "crm_refresh_request_created");
        event.put("title", "Solicitud de actualizacion CRM");
        event.put("message", request.agentName() + " solicita actualizar metricas CRM.");
        event.put("approveUrl", "/admin/zoho-crm/tasks/refresh-requests/" + request.id() + "/approve/async");
        event.put("rejectUrl", "/admin/zoho-crm/tasks/refresh-requests/" + request.id() + "/reject/async");
        messagingTemplate.convertAndSend("/topic/crm-refresh-requests", (Object) event);
    }

    private void publishRequestResolved(RefreshRequest request) {
        Map<String, Object> event = requestEvent(request);
        event.put("type", "crm_refresh_request_resolved");
        event.put("approved", request.status() == RequestStatus.APPROVED);
        event.put("title", request.status() == RequestStatus.APPROVED
                ? "Actualizacion CRM autorizada"
                : "Actualizacion CRM rechazada");
        event.put("message", request.status() == RequestStatus.APPROVED
                ? "Ya puedes actualizar CRM para el rango solicitado."
                : "Tu solicitud para actualizar CRM fue rechazada.");
        event.put("refreshUrl", agentRefreshUrl(request));
        messagingTemplate.convertAndSend("/topic/crm-refresh-authorizations", (Object) event);
    }

    private Map<String, Object> requestEvent(RefreshRequest request) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", request.id());
        event.put("agentName", request.agentName());
        event.put("agentEmail", request.agentEmail());
        event.put("from", request.from() != null ? request.from().toString() : null);
        event.put("to", request.to() != null ? request.to().toString() : null);
        event.put("taskType", request.taskType());
        event.put("operationCountry", request.operationCountry());
        event.put("status", request.statusFilter());
        event.put("size", request.size());
        event.put("requestedAt", request.requestedAt().toString());
        event.put("resolvedAt", request.resolvedAt() != null ? request.resolvedAt().toString() : null);
        return event;
    }

    private String agentRefreshUrl(RefreshRequest request) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/agent/zoho-crm/tasks/dashboard")
                .queryParam("from", request.from())
                .queryParam("to", request.to())
                .queryParam("size", request.size())
                .queryParam("refreshCrm", "true")
                .queryParam("crmPermissionStatus", "approved");
        if (request.taskType() != null && !request.taskType().isBlank()) {
            builder.queryParam("taskType", request.taskType());
        }
        if (request.operationCountry() != null && !request.operationCountry().isBlank()) {
            builder.queryParam("operationCountry", request.operationCountry());
        }
        if (request.statusFilter() != null && !request.statusFilter().isBlank()) {
            builder.queryParam("status", request.statusFilter());
        }
        return builder.build().toUriString();
    }

    private boolean matches(
            RefreshRequest request,
            String agentName,
            LocalDate from,
            LocalDate to,
            String taskType,
            String operationCountry,
            String status,
            Integer size
    ) {
        return Objects.equals(request.agentName(), agentName)
                && Objects.equals(request.from(), from)
                && Objects.equals(request.to(), to)
                && Objects.equals(request.taskType(), clean(taskType))
                && Objects.equals(request.operationCountry(), clean(operationCountry))
                && Objects.equals(request.statusFilter(), clean(status))
                && Objects.equals(request.size(), size);
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public enum RequestStatus {
        PENDING,
        APPROVED,
        REJECTED,
        USED
    }

    public record RefreshRequest(
            long id,
            String agentName,
            String agentEmail,
            LocalDate from,
            LocalDate to,
            String taskType,
            String operationCountry,
            String statusFilter,
            Integer size,
            RequestStatus status,
            LocalDateTime requestedAt,
            LocalDateTime resolvedAt
    ) {
        public RefreshRequest withStatus(RequestStatus newStatus) {
            return new RefreshRequest(
                    id,
                    agentName,
                    agentEmail,
                    from,
                    to,
                    taskType,
                    operationCountry,
                    statusFilter,
                    size,
                    newStatus,
                    requestedAt,
                    LocalDateTime.now()
            );
        }
    }
}
